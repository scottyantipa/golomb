package golomb

import ilog.concert._
import ilog.cp._

/*
  In mathematics, a Golomb ruler is a set of marks at integer positions along
  an imaginary ruler such that no two pairs of marks are the same distance apart.
  The number of marks on the ruler is its order, and the largest distance
  between two of its marks is its length.

  See https://en.wikipedia.org/wiki/Golomb_ruler for more information.

  The goal of this app is to try to find optimal rulers for a given order.
  Optimality means that any other ruler of that order is of the same or greater length.
  Translation and reflection of a Golomb ruler are considered trivial, so the smallest mark is customarily put
  at 0 and the next mark at the smaller of its two possible values.

  Examples:
  * For order 5: 2 solutions 0 1 4 9 11 ; 0 2 7 8 11
  * For order 10: 1 solution: 0 1 6 10 23 26 34 41 53 55
 */
object GolombRuler {
  System.loadLibrary("cp_wrap_cpp_java1290")
  solve()

  /*
    The problem statement:
    We will be given an order which is an int. This determines the number of marks.

    Known:
      - Number of marks (order)
    Unknown:
      - The value of each mark.
    Constraints:
      - No two pairs of marks can have the same distance apart
      - (bonus) Remove duplicate solutions like reflections/translations
        - first mark can always be initialized at 0 to ensure no translation duplicates
      - (bonus) make it a 'perfect' golomb ruler -- all distances <= the order can be measured
    Objectives:
      - Minimize length (the max of the marks)
   */
  def solve(): String = {
    val model: IloCP = new IloCP()
    val order = 3
    println(s"Solving for order $order...")

    // Dvars: All marks
    val marks = model.intVarArray(order, 0, 1000, "marks") // Note name is required for comparison later
    // Constraint: All marks different
    model.add(model.allDiff(marks.asInstanceOf[Array[IloIntExpr]]))

    // Constraint: Pin first mark to 0 to ensure no translation duplicates
    model.add(model.eq(marks.head, 0))

    // Constraint: Ensure no pair of marks has same distance as any other pair
    val deltas = marks.zipWithIndex.flatMap { rowAndIndex =>
      marks.zipWithIndex.flatMap {
        case (_, index2) if index2 == rowAndIndex._2 => None
        case (mark2, _) => Some(model.diff(rowAndIndex._1, mark2))
      }
    }
    model.add(model.allDiff(deltas))

    // Objective: minimize the length
    model.add(
      model.minimize(
        model.max(marks.asInstanceOf[Array[IloIntExpr]])
      )
    )

    // Solve
    model.setParameter(IloCP.DoubleParam.TimeLimit, 60 * 5)
    val res: String =
      if (model.solve()) {
        marks.map(model.getValue(_)).sorted.foreach(println)
        marks.map(model.getValue(_).toString).sorted.mkString(", ")
      } else {
        "Failed to solve"
      }
    model.end()
    res
  }
}
