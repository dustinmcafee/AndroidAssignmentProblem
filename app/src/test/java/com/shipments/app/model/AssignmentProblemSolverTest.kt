package com.shipments.app.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AssignmentProblemSolverTest {

    private val solver = AssignmentProblemSolver()
    private val calculator = SuitabilityScoreCalculator()

    private fun totalScore(matrix: Array<DoubleArray>, assignment: IntArray): Double =
        assignment.indices.sumOf { i -> matrix[i][assignment[i]] }

    /** Generate a random rows x cols matrix, padded to square with zeros. */
    private fun randomSquareMatrix(rows: Int, cols: Int, seed: Long): Array<DoubleArray> {
        val rng = Random(seed)
        val n = maxOf(rows, cols)
        return Array(n) { i ->
            DoubleArray(n) { j ->
                if (i < rows && j < cols) rng.nextDouble() * 100.0 else 0.0
            }
        }
    }

    /** Check that an assignment is a valid 1-to-1 mapping (no two rows share a column). */
    private fun isValidAssignment(assignment: IntArray): Boolean {
        val used = mutableSetOf<Int>()
        return assignment.all { used.add(it) }
    }

    /**
     * Compare solvers (no brute force for large n — too slow).
     */
    private fun assertAllSolversAgree(matrix: Array<DoubleArray>, label: String) {
        val jv = solver.findOptimalAssignment(matrix)
        val bf = solver.findOptimalAssignmentBellmanFord(matrix)
        val classic = solver.findOptimalAssignmentClassic(matrix)
        val jgrapht = solver.findOptimalAssignmentJGraphT(matrix)

        assertTrue("$label: JV invalid assignment", isValidAssignment(jv))
        assertTrue("$label: BF invalid assignment", isValidAssignment(bf))
        assertTrue("$label: Classic invalid assignment", isValidAssignment(classic))
        assertTrue("$label: JGraphT invalid assignment", isValidAssignment(jgrapht))

        val jvScore = totalScore(matrix, jv)
        val bfScore = totalScore(matrix, bf)
        val classicScore = totalScore(matrix, classic)
        val jgraphtScore = totalScore(matrix, jgrapht)

        assertEquals("$label: BF vs JV", jvScore, bfScore, 0.001)
        assertEquals("$label: Classic vs JV", jvScore, classicScore, 0.001)
        assertEquals("$label: JGraphT vs JV", jvScore, jgraphtScore, 0.001)
    }

    // --- 10x10 real data from input.json ---

    private val shipments = listOf(
        "215 Osinski Manors",
        "9856 Marvin Stravenue",
        "7127 Kathlyn Ferry",
        "987 Champlin Lake",
        "63187 Volkman Garden Suite 447",
        "75855 Dessie Lights",
        "1797 Adolf Island Apt. 744",
        "2431 Lindgren Corners",
        "8725 Aufderhar River Suite 859",
        "79035 Shanna Light Apt. 322"
    )

    private val drivers = listOf(
        "Everardo Welch",
        "Orval Mayert",
        "Howard Emmerich",
        "Izaiah Lowe",
        "Monica Hermann",
        "Ellis Wisozk",
        "Noemie Murphy",
        "Cleve Durgan",
        "Murphy Mosciski",
        "Kaiser Sose"
    )

    private fun buildScoreMatrix(): Array<DoubleArray> =
        Array(drivers.size) { i ->
            DoubleArray(shipments.size) { j ->
                calculator.calculate(shipments[j], drivers[i])
            }
        }

    @Test
    fun allSolversAgreeOnRealData() {
        val matrix = buildScoreMatrix()

        val bruteForce = solver.findOptimalAssignmentBruteForce(matrix)
        val jonkerVolgenant = solver.findOptimalAssignment(matrix)
        val bellmanFord = solver.findOptimalAssignmentBellmanFord(matrix)
        val classic = solver.findOptimalAssignmentClassic(matrix)
        val jgrapht = solver.findOptimalAssignmentJGraphT(matrix)

        val bruteForceScore = totalScore(matrix, bruteForce)
        val jvScore = totalScore(matrix, jonkerVolgenant)
        val bfScore = totalScore(matrix, bellmanFord)
        val classicScore = totalScore(matrix, classic)
        val jgraphtScore = totalScore(matrix, jgrapht)

        // All must match brute force (ground truth)
        assertEquals("Jonker-Volgenant vs brute force", bruteForceScore, jvScore, 0.001)
        assertEquals("Bellman-Ford vs brute force", bruteForceScore, bfScore, 0.001)
        assertEquals("Classic vs brute force", bruteForceScore, classicScore, 0.001)
        assertEquals("JGraphT vs brute force", bruteForceScore, jgraphtScore, 0.001)
    }

    // --- 3x3 known matrix ---

    @Test
    fun allSolversAgreeOn3x3() {
        // Optimal: row 0 → col 2 (9), row 1 → col 0 (7), row 2 → col 1 (8) = 24
        val matrix = arrayOf(
            doubleArrayOf(1.0, 2.0, 9.0),
            doubleArrayOf(7.0, 3.0, 4.0),
            doubleArrayOf(5.0, 8.0, 2.0)
        )

        val bruteForce = solver.findOptimalAssignmentBruteForce(matrix)
        val jonkerVolgenant = solver.findOptimalAssignment(matrix)
        val bellmanFord = solver.findOptimalAssignmentBellmanFord(matrix)
        val classic = solver.findOptimalAssignmentClassic(matrix)
        val jgrapht = solver.findOptimalAssignmentJGraphT(matrix)

        val expected = intArrayOf(2, 0, 1)
        assertArrayEquals("Brute force", expected, bruteForce)
        assertArrayEquals("Jonker-Volgenant", expected, jonkerVolgenant)
        assertArrayEquals("Bellman-Ford", expected, bellmanFord)
        assertArrayEquals("Classic", expected, classic)
        assertArrayEquals("JGraphT", expected, jgrapht)
    }

    // --- 1x1 trivial ---

    @Test
    fun allSolversHandleSingleElement() {
        val matrix = arrayOf(doubleArrayOf(5.0))
        val expected = intArrayOf(0)

        assertArrayEquals(expected, solver.findOptimalAssignmentBruteForce(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignment(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignmentBellmanFord(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignmentClassic(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignmentJGraphT(matrix))
    }

    // --- Empty matrix ---

    @Test
    fun allSolversHandleEmpty() {
        val matrix = emptyArray<DoubleArray>()
        val expected = intArrayOf()

        assertArrayEquals(expected, solver.findOptimalAssignmentBruteForce(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignment(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignmentBellmanFord(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignmentClassic(matrix))
        assertArrayEquals(expected, solver.findOptimalAssignmentJGraphT(matrix))
    }

    // --- Large and non-square matrices (no brute force — too slow) ---

    @Test
    fun allSolversAgreeOn100x100() {
        val matrix = randomSquareMatrix(100, 100, seed = 42)
        assertAllSolversAgree(matrix, "100x100")
    }

    @Test
    fun allSolversAgreeOn50x100() {
        // 50 drivers, 100 shipments — padded to 100x100 with zero rows
        val matrix = randomSquareMatrix(50, 100, seed = 123)
        assertAllSolversAgree(matrix, "50x100")
    }

    @Test
    fun allSolversAgreeOn100x50() {
        // 100 drivers, 50 shipments — padded to 100x100 with zero columns
        val matrix = randomSquareMatrix(100, 50, seed = 456)
        assertAllSolversAgree(matrix, "100x50")
    }
}
