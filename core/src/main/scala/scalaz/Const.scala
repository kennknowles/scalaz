package scalaz


sealed trait Const[A, B] {
  val value: A
}

object Const extends Consts

trait Consts {

  import ~>._

  def const[B]: (I ~> ({type λ[α] = Const[α, B]})#λ) = new (I ~> ({type λ[α] = Const[α, B]})#λ) {
    def apply[A](a: A) = new Const[A, B] {
      val value = a
    }
  }

  implicit def Const_^*^[A, B]: ^*^[Const[A, B], A] =
    ^*^.^*^(_.value, a => new Const[A, B] {
      val value = a
    })

  implicit def ConstShow[B: Show, A]: Show[Const[B, A]] =
    Show.UnpackShow[Const[B, A], B]

  implicit def ConstEqual[B: Equal, A]: Equal[Const[B, A]] =
    Equal.UnpackEqual[Const[B, A], B]

  implicit def ConstOrder[B: Order, A]: Order[Const[B, A]] =
    Order.UnpackOrder[Const[B, A], B]

}