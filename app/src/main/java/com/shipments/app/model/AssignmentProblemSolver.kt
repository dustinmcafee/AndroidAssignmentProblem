package com.shipments.app.model

import com.google.common.collect.Collections2.permutations
import org.jgrapht.alg.matching.KuhnMunkresMinimalWeightBipartitePerfectMatching
import org.jgrapht.alg.shortestpath.BellmanFordShortestPath
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.SimpleWeightedGraph
import java.math.BigDecimal

/**
 * Solves the assignment problem: given an NxN score matrix, find the 1-to-1
 * assignment of rows to columns that maximizes total score.
 *
 * Five implementations at different complexity levels:
 * - [findOptimalAssignment] — O(n^3), Jonker-Volgenant (Dijkstra + potentials)
 * - [findOptimalAssignmentBellmanFord] — O(n^4), successive shortest paths via Bellman-Ford
 * - [findOptimalAssignmentClassic] — O(n^4), Kuhn 1955 (matrix reduction + greedy matching)
 * - [findOptimalAssignmentJGraphT] — O(n^3), JGraphT's optimized Kuhn-Munkres (Edmonds-Karp 1972)
 * - [findOptimalAssignmentBruteForce] — O(n!), tries all permutations via Guava
 */
class AssignmentProblemSolver {

    /**
     * O(n^3) Jonker-Volgenant variant.
     *
     * Processes one driver at a time. For each driver, finds the cheapest
     * unassigned shipment by walking a shortest path through already-assigned
     * shipments. Potentials carry forward work between drivers so nothing
     * is ever recomputed — this is what makes it O(n^3) instead of O(n^4).
     *
     * @param profitMatrix NxN matrix where entry (i, j) is the score for assigning driver i to shipment j.
     * @return IntArray where the value at index i is the shipment index assigned to driver i.
     */
    fun findOptimalAssignment(profitMatrix: Array<DoubleArray>): IntArray {
        val n = profitMatrix.size
        if (n == 0) return intArrayOf()

        // Flip to minimization: high profit becomes low cost
        val maxVal = profitMatrix.maxOf { it.max() }

        // 1-indexed arrays; index 0 is a sentinel meaning "unassigned".
        // This avoids special-casing the first column in the path walk.
        val cost = Array(n + 1) { i ->
            DoubleArray(n + 1) { j ->
                if (i == 0 || j == 0) 0.0 else maxVal - profitMatrix[i - 1][j - 1]
            }
        }

        // Potentials shift edge weights so all reduced costs are non-negative,
        // which lets us use Dijkstra instead of Bellman-Ford.
        //
        // Reduced cost = cost[i][j] - rowPotential[i] - colPotential[j]
        //
        // Since both potentials are SUBTRACTED:
        //   increasing rowPotential[i] makes driver i's edges cheaper
        //   decreasing colPotential[j] makes shipment j's edges more expensive
        //
        // After each Dijkstra step, we increase row potentials for visited
        // drivers and decrease column potentials for visited shipments:
        //   visited driver + visited shipment   → cancels out, no change
        //   visited driver + unvisited shipment → cheaper (opens new paths)
        //   unvisited driver + visited shipment → more expensive (protects matches)
        //   unvisited driver + unvisited shipment → no change
        //
        // Same four cases as the O(n^4) version's matrix adjustment, but
        // done through potentials instead of modifying the cost matrix.
        val rowPotential = DoubleArray(n + 1)
        val colPotential = DoubleArray(n + 1)

        val colAssignment = IntArray(n + 1)  // which driver owns shipment j (0 = nobody)
        val prevCol = IntArray(n + 1)        // breadcrumb trail for path tracing

        // Process one driver at a time
        for (row in 1..n) {
            // Temporarily assign current driver to the sentinel column 0.
            // This is the starting point for the shortest path search.
            colAssignment[0] = row
            var cur = 0

            // cheapest[j] = cheapest reduced cost to reach shipment j from
            // any visited shipment so far. Starts at infinity for all.
            val cheapest = DoubleArray(n + 1) { Double.MAX_VALUE }
            val visited = BooleanArray(n + 1)

            // Walk from shipment to shipment through their current owners,
            // Dijkstra-style, until we reach an unassigned shipment.
            do {
                visited[cur] = true
                val curRow = colAssignment[cur]
                var best = Double.MAX_VALUE
                var next = -1

                // Check every unvisited shipment: can we reach it cheaper
                // by going through curRow than any path we've seen before?
                for (col in 1..n) {
                    if (visited[col]) continue

                    val rc = cost[curRow][col] - rowPotential[curRow] - colPotential[col]
                    if (rc < cheapest[col]) {
                        cheapest[col] = rc
                        prevCol[col] = cur  // remember how we got here
                    }
                    if (cheapest[col] < best) {
                        best = cheapest[col]
                        next = col
                    }
                }

                // rowPotential[i] += best → reduced cost goes down
                //   (makes edges from this driver cheaper)
                // colPotential[j] -= best → reduced cost goes up
                //   (makes edges to this shipment more expensive)
                //
                // When both happen (visited driver + visited shipment),
                //   they cancel out — no change.
                // When only row potential increases (visited driver +
                //   unvisited shipment), edges get cheaper — new paths open up.
                // When only col potential decreases (unvisited driver +
                //   visited shipment), edges get more expensive — protects
                //   existing assignments.
                //
                // cheapest[col] -= best keeps unvisited nodes' cached
                // reduced costs correct after the potential shifts.
                for (col in 0..n) {
                    if (visited[col]) {
                        rowPotential[colAssignment[col]] += best
                        colPotential[col] -= best
                    } else {
                        cheapest[col] -= best
                    }
                }

                cur = next
            } while (colAssignment[cur] != 0)

            // Found an unassigned shipment. Trace the breadcrumb trail back
            // to the sentinel, swapping assignments along the way so that
            // every shipment on the path gets its new owner.
            do {
                val prev = prevCol[cur]
                colAssignment[cur] = colAssignment[prev]
                cur = prev
            } while (cur != 0)
        }

        // Unlike the other functions, this algorithm tracks assignments as
        // shipment → driver (colAssignment) instead of driver → shipment,
        // because the path walk needs to quickly look up "who owns this
        // shipment?" at each step. Flip it to driver → shipment to match
        // the return format of the other functions.
        val result = IntArray(n)
        for (col in 1..n) {
            val driver = colAssignment[col] - 1   // convert to 0-indexed
            val shipment = col - 1                 // convert to 0-indexed
            result[driver] = shipment
        }
        return result
    }

    /**
     * O(n^4) successive shortest paths via Bellman-Ford.
     *
     * Assigns one driver at a time. For each driver, finds the cheapest
     * augmenting path — a chain of reassignments that frees up a shipment
     * for the new driver. Uses Bellman-Ford because the residual graph has
     * negative edges (the "refund" for unassigning a previous match).
     *
     * O(n^4) because: Bellman-Ford runs n-1 relaxation rounds, each O(n^2),
     * giving O(n^3) per driver. With n drivers, that's O(n^4).
     *
     * The O(n^3) Jonker-Volgenant version does the same thing but uses
     * Dijkstra + potentials to avoid negative edges, which is faster
     * because Dijkstra visits each node once instead of relaxing n times.
     *
     * @param profitMatrix NxN matrix where entry (i, j) is the score for assigning driver i to shipment j.
     * @return IntArray where the value at index i is the shipment index assigned to driver i.
     */
    fun findOptimalAssignmentBellmanFord(profitMatrix: Array<DoubleArray>): IntArray {
        val n = profitMatrix.size
        if (n == 0) return intArrayOf()

        // Flip to minimization: high profit becomes low cost
        val maxVal = profitMatrix.maxOf { it.max() }
        val cost = Array(n) { i -> DoubleArray(n) { j -> maxVal - profitMatrix[i][j] } }

        val rowMatch = IntArray(n) { -1 }  // rowMatch[i] = shipment assigned to driver i (-1 = unmatched)
        val colMatch = IntArray(n) { -1 }  // colMatch[j] = driver assigned to shipment j (-1 = unmatched)

        val source = n       // virtual node representing the new driver
        val sink = n + 1     // virtual node representing "done"

        // Assign one driver at a time. Each iteration guarantees exactly one
        // new match, so after n iterations every driver has a shipment.
        // Bellman-Ford runs once per iteration at O(n^3), so n iterations = O(n^4).
        for (row in 0 until n) {
            // Build a directed graph for this driver's augmenting path search.
            // Nodes: shipments (0..n-1), source (n), sink (n+1)
            val graph = SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
            for (v in 0..n + 1) graph.addVertex(v)

            // Source → each shipment: cost of directly assigning the new driver
            for (j in 0 until n) {
                val edge = graph.addEdge(source, j)
                graph.setEdgeWeight(edge, cost[row][j])
            }

            // Matched shipment j → other shipment k: cost of evicting j's
            // driver and reassigning them to k. The weight is the reassignment
            // cost minus the refund for freeing j.
            for (j in 0 until n) {
                if (colMatch[j] == -1) continue
                val driver = colMatch[j]
                for (k in 0 until n) {
                    if (k == j) continue
                    val edge = graph.addEdge(j, k)
                    graph.setEdgeWeight(edge, cost[driver][k] - cost[driver][j])
                }
            }

            // Unmatched shipments → sink: free to take, no cost
            for (j in 0 until n) {
                if (colMatch[j] == -1) {
                    val edge = graph.addEdge(j, sink)
                    graph.setEdgeWeight(edge, 0.0)
                }
            }

            // Find the cheapest chain of reassignments that frees up a
            // shipment for the new driver. Bellman-Ford is needed here because
            // the eviction "refunds" create negative edge weights (Can't use Dijkstra).
            val path = BellmanFordShortestPath(graph).getPath(source, sink)

            // The path looks like: source → A → B → C → sink
            // This means: give the new driver shipment A, move A's old driver
            // to shipment B, move B's old driver to shipment C (which was free).
            // Strip source and sink to get just the shipments in the chain.
            val shipments = path.vertexList.drop(1).dropLast(1)

            // The cheapest option for the new driver might be a shipment
            // that's already taken. Rather than settling for a worse shipment,
            // we give them the taken one and bump the displaced driver to
            // another shipment — who might bump another driver, and so on,
            // until the chain reaches a free shipment.
            //
            // Example: chain is [A, B, C] where C is free.
            //   1. Move B's old driver to C
            //   2. Move A's old driver to B
            //   3. Give the new driver shipment A
            //
            // We go backward so we read each driver's current shipment
            // before overwriting it.
            for (idx in shipments.lastIndex downTo 1) {
                val evictFrom = shipments[idx - 1]  // shipment losing its driver
                val moveTo = shipments[idx]          // shipment gaining that driver
                val driver = colMatch[evictFrom]     // the driver being moved
                rowMatch[driver] = moveTo
                colMatch[moveTo] = driver
            }
            // The first shipment in the chain goes to the new driver
            rowMatch[row] = shipments[0]
            colMatch[shipments[0]] = row
        }

        return rowMatch
    }

    /**
     * O(n^3) via JGraphT's Kuhn-Munkres implementation.
     *
     * O(n^3) via JGraphT's optimized Kuhn-Munkres (Edmonds-Karp 1972).
     *
     * Builds a weighted bipartite graph and delegates to JGraphT.
     * Same algorithm family as [findOptimalAssignmentClassic], but JGraphT
     * uses augmenting paths instead of greedy matching, which is what
     * makes it O(n^3) instead of O(n^4).
     *
     * @param profitMatrix NxN matrix where entry (i, j) is the score for assigning driver i to shipment j.
     * @return IntArray where the value at index i is the shipment index assigned to driver i.
     */
    fun findOptimalAssignmentJGraphT(profitMatrix: Array<DoubleArray>): IntArray {
        val n = profitMatrix.size
        if (n == 0) return intArrayOf()

        val graph = SimpleWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)

        // Drivers are vertices 0..n-1, shipments are vertices n..2n-1
        for (v in 0 until 2 * n) graph.addVertex(v)

        // Add edges with negative profit as weight — the algorithm minimizes,
        // but we want to maximize, so we negate.
        for (i in 0 until n) {
            for (j in 0 until n) {
                val edge = graph.addEdge(i, n + j)
                graph.setEdgeWeight(edge, -profitMatrix[i][j])
            }
        }

        val drivers = (0 until n).toSet()
        val shipments = (n until 2 * n).toSet()
        val matching = KuhnMunkresMinimalWeightBipartitePerfectMatching(graph, drivers, shipments).matching

        val result = IntArray(n)
        for (edge in matching.edges) {
            val source = graph.getEdgeSource(edge)
            val target = graph.getEdgeTarget(edge)
            val driver = if (source < n) source else target
            val shipment = (if (source < n) target else source) - n
            result[driver] = shipment
        }

        return result
    }

    /**
     * O(n^4) classic Hungarian algorithm (Kuhn, 1955).
     *
     * Easier to follow than the O(n^3) version: reduce rows/columns to create
     * zeros, match on zeros, adjust matrix if matching is incomplete, repeat.
     *
     * Uses BigDecimal to avoid floating point drift. The repeated subtract/add
     * adjustments cause Double values to accumulate rounding errors, which
     * breaks zero comparisons and produces wrong results at 100x100 scale.
     *
     * @param profitMatrix NxN matrix where entry (i, j) is the score for assigning driver i to shipment j.
     * @return IntArray where the value at index i is the shipment index assigned to driver i.
     */
    fun findOptimalAssignmentClassic(profitMatrix: Array<DoubleArray>): IntArray {
        val n = profitMatrix.size
        if (n == 0) return intArrayOf()

        // Flip to minimization: subtract each score from the max so high profit = low cost
        val maxVal = profitMatrix.maxOf { it.max() }
        val cost = Array(n) { i ->
            Array(n) { j -> BigDecimal.valueOf(maxVal - profitMatrix[i][j]) }
        }

        // Row reduction — make each row's minimum zero
        for (i in 0 until n) {
            val rowMin = cost[i].min()
            for (j in 0 until n) cost[i][j] = cost[i][j] - rowMin
        }

        // Column reduction — make each column's minimum zero
        for (j in 0 until n) {
            val colMin = (0 until n).minOf { i -> cost[i][j] }
            for (i in 0 until n) cost[i][j] = cost[i][j] - colMin
        }

        val rowMatch = IntArray(n) { -1 }  // rowMatch[i] = shipment assigned to driver i (-1 = unmatched)
        val colMatch = IntArray(n) { -1 }  // colMatch[j] = driver assigned to shipment j (-1 = unmatched)

        var matched = 0
        while (matched < n) {
            // Try to match each unassigned driver to a free zero-cost shipment
            for (i in 0 until n) {
                if (rowMatch[i] != -1) continue
                for (j in 0 until n) {
                    if (cost[i][j].compareTo(BigDecimal.ZERO) == 0 && colMatch[j] == -1) {
                        rowMatch[i] = j
                        colMatch[j] = i
                        break
                    }
                }
            }

            matched = rowMatch.count { it != -1 }
            if (matched == n) break

            // Not all drivers got a shipment. We need to figure out which parts of
            // the cost matrix to adjust so that new zero-cost entries appear.
            // To do that, mark every driver and shipment that's connected to an
            // unmatched driver through a chain of zero-cost entries.
            val reachableRow = BooleanArray(n)
            val reachableCol = BooleanArray(n)
            for (i in 0 until n) {
                if (rowMatch[i] == -1) reachableRow[i] = true
            }
            var changed = true
            while (changed) {
                changed = false
                for (i in 0 until n) {
                    if (!reachableRow[i]) continue
                    for (j in 0 until n) {
                        if (!reachableCol[j] && cost[i][j].compareTo(BigDecimal.ZERO) == 0) {
                            reachableCol[j] = true
                            changed = true
                        }
                    }
                }
                for (j in 0 until n) {
                    if (!reachableCol[j]) continue
                    val r = colMatch[j]
                    if (r != -1 && !reachableRow[r]) {
                        reachableRow[r] = true
                        changed = true
                    }
                }
            }

            // The greedy matching above couldn't pair every driver. The reason
            // is that there aren't enough zero-cost cells in the right places.
            // We need to create new zeros by shifting costs, without breaking
            // existing zeros we depend on.
            //
            // Step 1: Find the smallest cost among cells that connect a
            //         reachable driver to an unreachable shipment. These are
            //         the "closest to zero" cells that could become new options.
            var minUncovered = BigDecimal.valueOf(Double.MAX_VALUE)
            for (i in 0 until n) {
                if (!reachableRow[i]) continue
                for (j in 0 until n) {
                    if (!reachableCol[j] && cost[i][j] < minUncovered) {
                        minUncovered = cost[i][j]
                    }
                }
            }

            // Step 2: Adjust costs so new zero-cost cells appear for the next
            //         matching attempt. Each cell falls into one of four cases:
            //
            //   Reachable driver + unreachable shipment → subtract (costs go down,
            //       some hit zero and become new matching candidates)
            //   Unreachable driver + reachable shipment → add (costs go up,
            //       protects existing matches from being stolen)
            //   Reachable driver + reachable shipment → both cancel out, no change
            //   Unreachable driver + unreachable shipment → neither applies, no change
            for (i in 0 until n) {
                for (j in 0 until n) {
                    if (reachableRow[i]) cost[i][j] = cost[i][j] - minUncovered
                    if (reachableCol[j]) cost[i][j] = cost[i][j] + minUncovered
                }
            }

            // Wipe all matches and try greedy matching again from scratch.
            //
            // Why this is O(n^4):
            // We need n matches total. Each adjustment creates new zeros, but
            // greedy might not use them (it grabs something else first), so
            // it can take up to n adjustments before greedy finds one more
            // match. That's up to n^2 total adjustments, each doing O(n^2)
            // work (reachability + find min + adjust matrix) = O(n^4).
            //
            // The O(n^3) version avoids this by extending its search
            // incrementally after each adjustment — O(n) per adjustment
            // instead of O(n^2) — giving n^2 adjustments × O(n) = O(n^3).
            rowMatch.fill(-1)
            colMatch.fill(-1)
        }

        return rowMatch
    }

    /**
     * O(n!) brute force. Tries every permutation via Guava. Only usable for small n.
     *
     * @param profitMatrix NxN matrix where entry (i, j) is the score for assigning driver i to shipment j.
     * @return IntArray where the value at index i is the shipment index assigned to driver i.
     */
    fun findOptimalAssignmentBruteForce(profitMatrix: Array<DoubleArray>): IntArray {
        val n = profitMatrix.size
        if (n == 0) return intArrayOf()

        var bestScore = Double.NEGATIVE_INFINITY
        var bestAssignment = IntArray(n)

        for (perm in permutations((0 until n).toList())) {
            var score = 0.0
            for (i in 0 until n) {
                score += profitMatrix[i][perm[i]]
            }
            if (score > bestScore) {
                bestScore = score
                bestAssignment = perm.toIntArray()
            }
        }

        return bestAssignment
    }
}
