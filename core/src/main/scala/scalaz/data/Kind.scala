package scalaz
package data

import concurrent.{Promise, Strategy}
import annotation.tailrec

trait *[A] {
  val value: A

  import *._
  import newtypes._
  import StateT._
  import WriterT._
  import LazyTuple._
  import Validation._
  import ~>._
  import wrap.StreamW._

  def pack[B](implicit p: Pack[B, A]): B =
    p.pack(value)

  def unpack[B](implicit p: Unpack[A, B]): B =
    p.unpack(value)

  sealed trait Ala[N, O] {
    def apply[P, Q](k: A => (O => N) => P)(implicit n1: Pack[N, O], n2: Unpack[P, Q]): Q
  }

  def ala[N, O]: Ala[N, O] = new Ala[N, O] {
    def apply[P, Q](k: A => (O => N) => P)(implicit n1: Pack[N, O], n2: Unpack[P, Q]): Q =
      n2.unpack(k(value)(n1.pack))
  }

  sealed trait Alaa[N, O] {
    def apply[P, Q, Y](k: A => (Y => N) => P)(implicit n1: Pack[N, O], n2: Unpack[P, Q]): (Y => O) => Q
  }

  def alaa[N, O]: Alaa[N, O] = new Alaa[N, O] {
    def apply[P, Q, Y](k: A => (Y => N) => P)(implicit n1: Pack[N, O], n2: Unpack[P, Q]): (Y => O) => Q =
      j => n2.unpack(k(value)(y => n1.pack(j(y))))
  }

  def allaa[N, O](k: A => (O => N) => N)(implicit n: Newtype[N, O]): O = {
    implicit val pack = n.pack
    implicit val unpack = n.unpack
    ala(k)
  }

  def point[F[_]](implicit p: Pointed[F]): F[A] =
    p point value

  /**Alias for {@link #pure}*/
  def η[F[_]](implicit p: Pointed[F]): F[A] =
    point[F]

  def dual: Dual[A] =
    value.pack[Dual[A]]

  /**Alias for {@link #dual}*/
  def σ : Dual[A] =
    dual

  def |+|(a: => A)(implicit s: Semigroup[A]): A =
    s append (value, a)

  /**Alias for {@link #|+|}*/
  def ⊹(a: => A)(implicit s: Semigroup[A]): A =
    |+|(a)

  def ===(a: A)(implicit e: Equal[A]): Boolean =
    e.equal(value)(a)

  /**Alias for {@link scalaz.Identity#===}*/
  def ≟(a: A)(implicit e: Equal[A]): Boolean =
    ===(a)

  def /==(a: A)(implicit e: Equal[A]): Boolean =
    !(===(a))

  /**Alias for {@link #/==}*/
  def ≠(a: A)(implicit e: Equal[A]): Boolean =
    /==(a)

  /**
   * Returns `a` if it is non-null, otherwise returns `d`.
   */
  def ??(d: => A)(implicit ev: Null <:< A): A =
    Option(value) getOrElse d

  /**
   * Raises an error if `value ≠ b`, according to the given `Equal`. The message is formated with the given `Show`.
   */
  // using the implicit parameter ev here gives better compiler error messages for mistyped expressions like  1 assert_=== "".
  // the simpler signature is def assert_===(b: A)(implicit e: Equal[A], s: Show[A])
  def assert_===[B](b: B)(implicit e: Equal[A], s: Show[A], ev: B <:< A) =
    if (/==(b)) error(shows + " ≠ " + ev(b).shows)

  /**Alias for  {@link #assert_===}*/
  def assert_≟[B](b: B)(implicit e: Equal[A], s: Show[A], ev: B <:< A) =
    assert_===(b)

  def ?|?(a: A)(implicit o: Order[A]): Ordering =
    o.order(value)(a)

  def lte(a: A)(implicit o: Order[A]): Boolean =
    o.isLTE(value)(a)

  def gte(a: A)(implicit o: Order[A]): Boolean =
    o.isGTE(value)(a)

  def lt(a: A)(implicit o: Order[A]): Boolean =
    o.isLT(value)(a)

  def gt(a: A)(implicit o: Order[A]): Boolean =
    o.isGT(value)(a)

  def min(a: A)(implicit o: Order[A]): A =
    o.min(value)(a)

  def max(a: A)(implicit o: Order[A]): A =
    o.max(value)(a)

  def show(implicit s: Show[A]): List[Char] =
    s.show(value)

  def shows(implicit s: Show[A]): String =
    s.show(value).mkString

  def print(implicit s: Show[A]): Unit =
    Console.print(shows)

  def println(implicit s: Show[A]): Unit =
    Console.println(shows)

  def mapply[F[_], B](f: F[A => B])(implicit ftr: Functor[F]): F[B] =
    ftr.fmap((ff: A => B) => ff(value))(f)

  // Yeah awesome, totally.
  def |>[B](f: A => B): B =
    f(value)

  def text(implicit s: Show[A]): xml.Text =
    s.text(value)

  def <===>(a: A)(implicit m: MetricSpace[A]): Int =
    m.distance(value)(a)

  def constantStateT[F[_], S](s: => S)(implicit p: Pointed[F]): StateT[S, F, A] =
    StateT.stateT((_: S) => p.point(value, s))

  def stateT[F[_], S](implicit p: Pointed[F]): StateT[S, F, A] =
    StateT.stateT(s => p.point(value, s))

  def constantState[S](s: => S): State[S, A] =
    StateT.state((_: S) => (value, s))

  def state[S]: State[S, A] =
    StateT.state((value, _: S))

  def unfold[F[_], B](f: A => Option[(B, A)])(implicit p: Pointed[F], m: Monoid[F[B]]): F[B] =
    f(value) match {
      case None => m.z
      case Some((b, a)) => m.append(p.point(b), a.unfold(f))
    }

  def replicate[F[_]](n: Int, f: A => A = a => a)(implicit p: Pointed[F], m: Monoid[F[A]]): F[A] = {
    @tailrec
    def replicate0(accum: F[A], n: Int, a: A): F[A] =
      if (n > 0) replicate0(m.append(accum, p.point(a)), n - 1, f(a)) else accum

    replicate0(m.z, n, value)
  }

  def repeat[F[_]](implicit p: Pointed[F], m: Semigroup[F[A]]): F[A] =
    m.append(p.point(value), repeat)

  def iterate[F[_]](f: A => A)(implicit p: Pointed[F], m: Semigroup[F[A]]): F[A] =
    m.append(p.point(value), f(value).iterate(f))

  def zipper: Zipper[A] =
    Zipper.zipper(Stream.empty, value, Stream.empty)

  def unfoldTree[B](f: A => (B, () => Stream[A])): Tree[B] =
    f(value) match {
      case (a, bs) => Tree.node(a, bs().unfoldForest(f))
    }

  def unfoldTreeM[B, M[_]](f: A => M[(B, Stream[A])])(implicit m: Monad[M]): M[Tree[B]] =
    m.bd((abs: (B, Stream[A])) =>
      m.fmap((ts: Stream[Tree[B]]) =>
        Tree.node(abs._1, ts))(abs._2.unfoldForestM[B, M](f)))(f(value))

  def node(subForest: Tree[A]*): Tree[A] =
    Tree.node(value, subForest.toStream)

  def leaf: Tree[A] =
    Tree.leaf(value)

  def success[X]: Validation[X, A] =
    Validation.success[X](value)

  def successNel[X]: ValidationNEL[X, A] =
    success

  def fail[X]: Validation[A, X] =
    Validation.failure[X](value)

  def some: Option[A] =
    point[Option]

  def squared: (A, A) =
    (value, value)

  def left[B]: Either[A, B] =
    Left(value)

  def right[B]: Either[B, A] =
    Right(value)

  def wrapNel: NonEmptyList[A] =
    NonEmptyList.nel(value, Nil)

  /**
   * @return the result of pf(value) if defined, otherwise the the Zero element of type B.
   */
  def matchOrZero[B: Zero](pf: PartialFunction[A, B]): B =
    pf.lift(value) match {
      case None => implicitly[Zero[B]].zero
      case Some(x) => x
    }

  @tailrec
  final def doWhile(f: A => A, p: A => Boolean): A = {
    val x = f(value)
    if (p(x)) x.doWhile(f, p) else x
  }

  @tailrec
  final def whileDo(f: A => A, p: A => Boolean): A =
    if (p(value)) f(value).whileDo(f, p) else value

  /**A pair lazy in its right value, with this value on the left and the given value on the right. **/
  def <&>[B](b: => B): (A :&: B) =
    lazyTuple2(value, b)

  /**Convert the value into a monoid **/
  def unit[M](implicit r: Reducer[A, M], mm: Monoid[M]): M =
    r.unit(value)

  /**Convert the value into a monoid in a pointed functor **/
  def pointUnit[M[_], N](implicit m: Pointed[M], r: Reducer[A, N], mm: Monoid[N]): M[N] =
    m.point(unit[N])

  /**Append the value to a monoid for use in left-to-right reduction **/
  def snoc[C](c: C)(implicit r: Reducer[C, A], mm: Monoid[A]): A =
    r.snoc(value, c)

  /**Prepend the value to a monoid for use in right-to-left reduction **/
  def cons[M](m: M)(implicit r: Reducer[A, M], mm: Monoid[M]): M =
    r.cons(value, m)

  /**Constructs a writer with the given value for writing */
  def setT[F[_]](implicit p: Pointed[F]): (I ~> ({type λ[β] = WriterT[A, F, β]})#λ) =
    new (I ~> ({type λ[β] = WriterT[A, F, β]})#λ) {
      def apply[Z](z: Z) = writerT[A, F, Z](p.point((value, z)))
    }

  /**Constructs a writer with the given value for writing */
  def set[W](w: W): Writer[W, A] =
    writer[W, A](w, value)

  def visit[F[_] : Pointed](p: PartialFunction[A, F[A]]): F[A] =
    if (p isDefinedAt value) p(value)
    else implicitly[Pointed[F]].point(value)

}

object * extends **

trait ** {
  implicit def **[A](a: A): *[A] = new *[A] {
    val value = a
  }
}

// formerly MA
trait *->*[F[_], A] {
  val value: F[A]

  import *->*._
  import *._
  import Const._
  import WriterT._
  import BKTree._
  import ~>._

  /**
   * Use this to force implicit conversion of F[A] to *->*[F, A]. Useful when the original
   * type contains a member with the same name as *->*, for example:
   *
   * scala> List(1, 2, 3).min
   * res0: Int = 1
   * scala> List(1, 2, 3).as.min
   * res1: Option[Int] = Some(1)
   *
   * This is less noisy than the alternative:
   *
   * scala> (List(1, 2, 3): *->*[List, Int]).min
   * res2: Option[Int] = Some(1)
   */
  def as: *->*[F, A] =
    this

  def ∘[B](f: A => B)(implicit ftr: Functor[F]): F[B] =
    ftr.fmap(f)(value)

  def map[B](f: A => B)(implicit ftr: Functor[F]): F[B] =
    ∘(f)

  def flatMap[B](f: A => F[B])(implicit b: Bind[F]): F[B] =
    b.bind(f)(value)

  def foreach(f: A => Unit)(implicit e: Each[F]): Unit =
    e.each(f)(value)

  def map2[G[_], B, C](f: B => C)(implicit m: A =:= G[B], f1: Functor[F], f2: Functor[G]): F[G[C]] =
    ∘(k => (k: G[B]) ∘ f)

  def ∘∘[G[_], B, C](f: B => C)(implicit m: A =:= G[B], f1: Functor[F], f2: Functor[G]): F[G[C]] =
    map2(f)

  /**
   * Returns a *->* with the type parameter `F` equal to [A] F[G[A]], given that type `A` is constructed from type constructor `G`.
   * This allows composition of type classes for `F` and `G`. For example:
   * <code>(List(List(1)).comp.map {2 +}) assert_=== List(List(3))</code>
   */
  def comp[G[_], B](implicit n: A =:= G[B], f: Functor[F]): *->*[({type λ[α] = F[G[α]]})#λ, B] =
    **->**[({type λ[α] = F[G[α]]})#λ, B](value ∘ n)

  def >|[B](f: => B)(implicit t: Functor[F]): F[B] =
    ∘(_ => f)

  def foldMap[M](z: A => M)(implicit m: Monoid[M], f: Foldr[F]): M =
    f.foldr[A, M]((a: A) => b => m.append(z(a), b))(m.z)(value)

  def foldMapDefault[B](f: A => B)(implicit t: Traverse[F], m: Monoid[B]): B = {
    val k: F[A] => Const[B, F[B]] = t.traverse[({type λ[α] = Const[B, α]})#λ, A, B](a => const[B](f(a)))
    k(value).value
  }

  def collapse(implicit t: Traverse[F], m: Monoid[A]): A =
    foldMapDefault(identity[A])


  /**
   * Accumulates values *->*[A] and *->*[B], and returns an ApplicativeBuilder that can accumulate
   * further such values. These values can be then applied to a provided function through the Applicative Functor for F.
   *
   * Example:
   *
   *  (1.some ⊛ 2.some) apply { (a, b, c) => a + b + c) } === Some(3)
   *  (1.some ⊛ 2.some ⊛ 3.some) tupled === Some((1, 2, 3))
   *
   * @return An ApplicativeBuilder that has accumulated `value: *->*[A]` and `b: *->*[B]`.
   */
  def |@|[B](bb: F[B]): ApplicativeBuilder[F, A, B] =
    new ApplicativeBuilder[F, A, B] {
      val a = value
      val b = bb
    }

  def =>>[B](f: F[A] => B)(implicit w: CoBind[F]): F[B] =
    w.coBind(f)(value)

  def coPoint(implicit p: CoPointed[F]): A =
    p coPoint value

  def coJoin(implicit j: CoJoin[F]): F[F[A]] =
    j coJoin value

  /**Alias for |@| */
  def ⊛[B](bb: F[B]): ApplicativeBuilder[F, A, B] =
    |@|(bb)

  def <*>[B](f: F[A => B])(implicit a: Applic[F]): F[B] =
    a.applic(f)(value)

  def <**>[B, C](b: F[B])(f: (A, B) => C)(implicit ap: ApplicFunctor[F]): F[C] =
    ap(ap.fmap(f.curried)(value))(b)

  def *>[B](b: F[B])(implicit ap: ApplicFunctor[F]): F[B] =
    <**>(b)((_, b) => b)

  def <*[B](b: F[B])(implicit ap: ApplicFunctor[F]): F[A] =
    <**>(b)((a, _) => a)

  def <|*|>[B](b: F[B])(implicit ap: ApplicFunctor[F]): F[(A, B)] =
    <**>(b)((_, _))

  def traverse[G[_], B](f: A => G[B])(implicit a: Applicative[G], t: Traverse[F]): G[F[B]] = {
    val k = t.traverse(f)
    k(value)
  }

  def traverse_[G[_], B](f: A => G[B])(implicit a: Applicative[G], t: Foldl[F]): G[Unit] = {
    implicit val p = a.pointed
    implicit val ap = a.applicFunctor
    value.foldl(().point)((x => y => x <* f(y)))
  }

  def >>=[B](f: A => F[B])(implicit b: Bind[F]): F[B] =
    b.bind(f)(value)

  def >|>[B](f: => F[B])(implicit b: Bind[F]): F[B] =
    b.bind((_: A) => f)(value)

  def join[B](implicit m: A =:= F[B], j: Bind[F]): F[B] =
    >>=(m)

  def μ[B](implicit m: A =:= F[B], j: Bind[F]): F[B] =
    join

  def forever[B](implicit b: Bind[F]): F[B] =
    value >|> value.forever

  def <+>(z: => F[A])(implicit p: Plus[F]): F[A] =
    p.plus(value, z)

  def +>:(a: A)(implicit s: Semigroup[F[A]], q: Pointed[F]): F[A] =
    s append (q.point(a), value)

  def <+>:(a: A)(implicit p: Plus[F], q: Pointed[F]): F[A] =
    p.plus(q.point(a), value)

  def foldl[B](b: B)(f: B => A => B)(implicit r: Foldl[F]): B =
    r.foldl[A, B](f)(b)(value)

  def foldl1(f: A => A => A)(implicit r: Foldl[F]): Option[A] =
    r.foldl1[A](f)(value)

  def foldr[B](b: B)(f: A => (=> B) => B)(implicit r: Foldr[F]): B =
    r.foldr[A, B](f)(b)(value)

  def foldr1(f: A => (=> A) => A)(implicit r: Foldr[F]): Option[A] =
    r.foldr1[A](f)(value)

  def foldlM[G[_], B](b: B)(f: B => A => G[B])(implicit fl: Foldl[F], m: Monad[G]): G[B] = {
    implicit val p = m.pointed
    implicit val d = m.bind
    foldl[G[B]](b.point[G])(b => a => b flatMap ((z: B) => f(z)(a)))
  }

  def foldrM[G[_], B](b: B)(f: A => B => G[B])(implicit fr: Foldr[F], m: Monad[G]): G[B] = {
    implicit val p = m.pointed
    implicit val d = m.bind
    foldr[G[B]](b.point[G])(a => b => b flatMap ((z: B) => f(a)(z)))
  }

  def replicateM[G[_]](n: Int)(implicit m: Monad[F], p: Pointed[G], d: Monoid[G[A]]): F[G[A]] = {
    implicit val pp = m.pointed
    implicit val ft = m.functor
    implicit val bd = m.bind
    implicit val s = d.semigroup
    if (n <= 0) d.z.point[F]
    else flatMap(a => replicateM[G](n - 1) map (a +>: _))
  }

  def replicateM_(n: Int)(implicit m: Monad[F]): F[Unit] = {
    implicit val pp = m.pointed
    implicit val ft = m.functor
    implicit val bd = m.bind

    def replicateM__(a: F[Unit], i: Int): F[Unit] =
      if (i > 0) replicateM__(>|>(a), i - 1) else a
    replicateM__(().point[F], n)
  }

  def zipWithA[G[_], B, C](b: F[B])(f: A => B => G[C])(implicit a: Applicative[F], t: Traverse[F], z: Applicative[G]): G[F[C]] = {
    implicit val ft = a.functor
    implicit val ap = a.applic
    b.<*>(map(f)).sequence[G, C]
  }

  def listl(implicit r: Foldl[F]): List[A] = {
    val b = new scala.collection.mutable.ListBuffer[A]
    foldl(())(_ => a => b += a)
    b.toList
  }

  def listr(implicit r: Foldr[F]): List[A] =
    foldr(Nil: List[A])(x => y => x :: y)

  def stream(implicit r: Foldr[F]): Stream[A] =
    foldr(Stream.empty[A])(x => y => Stream.cons(x, y))

  def !(n: Int)(implicit i: Index[F]): Option[A] =
    i.index(value)(n)

  def -!-(n: Int)(implicit i: Index[F]): A =
    this.!(n) getOrElse (error("Index " + n + " out of bounds"))

  def indexOr(n: Int, d: => A)(implicit i: Index[F]): A =
    i.indexOr(value, d)(n)

  def any(p: A => Boolean)(implicit r: Foldr[F]): Boolean =
    foldr(false)(x => y => p(x) || y)

  def ∃(p: A => Boolean)(implicit r: Foldr[F]): Boolean =
    any(p)

  def all(p: A => Boolean)(implicit r: Foldr[F]): Boolean =
    foldr(true)(x => y => p(x) && y)

  def ∀(p: A => Boolean)(implicit r: Foldr[F]): Boolean =
    all(p)

  def empty(implicit r: Foldr[F]): Boolean =
    all(_ => false)

  def element(a: A)(implicit r: Foldr[F], eq: Equal[A]): Boolean =
    any(eq.equal(a))

  def ∋(a: A)(implicit r: Foldr[F], eq: Equal[A]): Boolean =
    element(a)

  /**
   * Splits the elements into groups that alternatively satisfy and don't satisfy the predicate p.
   */
  def splitWith(p: A => Boolean)(implicit r: Foldr[F]): List[List[A]] =
    foldr((Nil: List[List[A]], None: Option[Boolean]))(a => b => {
      val pa = p(a)
      (b match {
        case (_, None) => List(List(a))
        case (x, Some(q)) => if (pa == q) (a :: x.head) :: x.tail else List(a) :: x
      }, Some(pa))
    })._1

  /**
   * Selects groups of elements that satisfy p and discards others.
   */
  def selectSplit(p: A => Boolean)(implicit r: Foldr[F]): List[List[A]] =
    foldr((Nil: List[List[A]], false))(a => xb => xb match {
      case (x, b) => {
        val pa = p(a)
        (if (pa)
          if (b)
            (a :: x.head) :: x.tail
          else
            List(a) :: x
        else x, pa)
      }
    })._1

  def suml(implicit r: Foldl[F], m: Monoid[A]): A =
    foldl(m.z)(x => y => m append (x, y))

  def sumr(implicit r: Foldr[F], m: Monoid[A]): A =
    foldr(m.z)(x => y => m append (x, y))

  def len(implicit l: Length[F]): Int =
    l len value

  def max(implicit r: Foldl[F], ord: Order[A]): Option[A] =
    foldl1((x: A) => (y: A) => if (ord.isGT(x)(y)) x else y)

  def maxOr(d: => A)(implicit r: Foldl[F], ord: Order[A]): A =
    max getOrElse d

  def min(implicit r: Foldl[F], ord: Order[A]): Option[A] =
    foldl1((x: A) => (y: A) => if (ord.isLT(x)(y)) x else y)

  def minOr(d: => A)(implicit r: Foldl[F], ord: Order[A]): A =
    min getOrElse d

  def longDigits(implicit d: A =:= Digit, t: Foldl[F]): Long =
    foldl(0L)(n => a => n * 10L + (a: Digit))

  def digits(implicit c: A =:= Char, t: Functor[F]): OptionT[F, Digit] =
    OptionT.optionT(map((a: A) => Digit.digitFromChar(a)))

  def digitsOr(d: => Digit)(implicit c: A =:= Char, t: Functor[F]): F[Digit] =
    map((a: A) => Digit.digitFromChar(a) getOrElse d)

  def digitsCollapse(implicit c: A =:= Char, t: MonadEmpty[F]): F[Digit] =
    t.bd((a: A) => Digit.digitFromChar(a) match {
      case None => t.e[Digit]
      case Some(d) => t.point(d)
    })(value)

  // generalised catMaybes
  def unite[T[_], B](implicit c: A =:= T[B], t: MonadEmptyPlus[F], f: Foldr[T]): F[B] =
    t.bd((a: A) => f.foldr[B, F[B]](z => b => t.pl(t.point(z), b))(t.e[B])(c(a)))(value)

  def sequence[G[_], B](implicit a: A =:= G[B], t: Traverse[F], p: Applicative[G]): G[F[B]] =
    traverse((z: A) => (z: G[B]))

  def traverseDigits(implicit c: A =:= Char, t: Traverse[F]): Option[F[Digit]] = {
    implicit val ftr = t.functor
    val k = map((f: A) => (f: Char)).digits.runT.sequence
    k
  }

  def traverseDigitsOr(d: => F[Digit])(implicit c: A =:= Char, t: Traverse[F]): F[Digit] =
    traverseDigits getOrElse d

  def <--->(w: F[A])(implicit l: Length[F], ind: Index[F], equ: Equal[A]): Int = {
    import Memo._
    def levenshteinMatrix(w: F[A])(implicit l: Length[F], ind: Index[F], equ: Equal[A]): (Int, Int) => Int = {
      val m = mutableHashMapMemo[(Int, Int), Int]

      def get(i: Int, j: Int): Int = if (i == 0) j
      else if (j == 0) i
      else {
        lazy val t = this -!- (i - 1)
        lazy val u = w -!- (j - 1)
        lazy val e = equ.equal(t)(u)

        val g = m {
          case (a, b) => get(a, b)
        }
        val a = g((i - 1, j)) + 1
        val b = g((i - 1, j - 1)) + (if (e) 0 else 1)
        def c = g((i, j - 1)) + 1
        if (a < b) a else if (b <= c) b else c
      }

      get
    }

    val k = levenshteinMatrix(w)
    k(l.len(value), l.len(w))
  }

  /**Puts the given write value into a writer transformer and associates with this M[A] value */
  def put[W](w: W)(implicit f: Functor[F]): WriterT[W, F, A] =
    writerT[W, F, A](map(a => (w, a)))

  /**Puts the write value that is produced by applying the given function into a writer transformer and associates with this M[A] value */
  def putWith[W](w: A => W)(implicit f: Functor[F]): WriterT[W, F, A] =
    writerT[W, F, A](map(a => (w(a), a)))

  /**Puts the given write value into a writer transformer, lifted into a pointed functor, and associates with this M[A] value */
  def liftw[G[_]](implicit f: Functor[F], p: Pointed[G]): (I ~> (({type λ[α] = WriterT[G[α], F, A]})#λ)) =
    new (I ~> (({type λ[α] = WriterT[G[α], F, A]})#λ)) {
      def apply[W](w: W): WriterT[G[W], F, A] =
        error("")

      // writerT[G[W], F, A](map(a => (w.value.point[G], a)))
    }

  /**Puts the given write value that is produced by applying the given function into a writer transformer, lifted into a pointed functor, and associates with this M[A] value */
  def liftwWith[G[_]](implicit f: Functor[F], p: Pointed[G]): (((({type λ[α] = Function1[A, α]})#λ)) ~> (({type λ[α] = WriterT[G[α], F, A]})#λ)) =
    new (((({type λ[α] = Function1[A, α]})#λ)) ~> (({type λ[α] = WriterT[G[α], F, A]})#λ)) {
      def apply[W](w: A => W) =
        writerT[G[W], F, A](map(a => (w(a).point[G], a)))
    }

  def ifM[B](t: => F[B], f: => F[B])(implicit a: Bind[F], b: A =:= Boolean): F[B] =
    flatMap((x: A) => if (x) t else f)

  def bktree(implicit f: Foldl[F], m: MetricSpace[A]) =
    foldl(emptyBKTree[A])(x => y => x + y)

  def fpair(implicit f: Functor[F]): F[(A, A)] =
    map(a => (a, a))

  def lift[G[_[_], _]](implicit m: Monad[F], t: MonadTrans[G]): G[F, A] =
    t.lift(value)(m)

  def fpoint[G[_]](implicit f: Functor[F], p: Pointed[G]): F[G[A]] =
    map(a => p.point(a))

  def foldReduce[B](implicit f: Foldr[F], r: Reducer[A, B]): B = {
    implicit val mm = r.monoid
    foldMap(r.unit(_))
  }

  // This uses (sequence . map) instead of traverse since it needs to be fully strict.
  def parMap[B](f: A => B)(implicit s: Strategy, t: Traverse[F]): Promise[F[B]] = {
    implicit val ftr = t.functor
    map(a => Promise.promise(f(a))).sequence
  }

  def parBind[B](f: A => F[B])(implicit m: Monad[F], s: Strategy, t: Traverse[F]): Promise[F[B]] =
    parMap(f).map(k => m.jn(k))

  def parZipWith[B, C](f: A => B => C)(bs: F[B])(implicit z: Applicative[F], s: Strategy, t: Traverse[F]): Promise[F[C]] = {
    zipWithA(bs)(x => y => Promise.promise(f(x)(y)))
  }
}

object *->* extends **->**

trait **->** {
  implicit def **->**[F[_], A](a: F[A]): (F *->* A) = new (F *->* A) {
    val value = a
  }
}

sealed trait ApplicativeBuilder[M[_], A, B] {
  val a: M[A]
  val b: M[B]

  def apply[C](f: (A, B) => C)(implicit ap: ApplicFunctor[M]): M[C] = ap(ap.fmap(f.curried)(a))(b)

  def tupled(implicit ap: ApplicFunctor[M]): M[(A, B)] = apply(Tuple2.apply)

  def ⊛[C](cc: M[C]) = new ApplicativeBuilder3[C] {
    val c = cc
  }

  def |@|[C](cc: M[C]) = ⊛(cc)

  sealed trait ApplicativeBuilder3[C] {
    val c: M[C]

    def apply[D](f: (A, B, C) => D)(implicit ap: ApplicFunctor[M]): M[D] = ap(ap(ap.fmap(f.curried)(a))(b))(c)

    def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C)] = apply(Tuple3.apply)

    def ⊛[D](dd: M[D]) = new ApplicativeBuilder4[D] {
      val d = dd
    }

    def |@|[D](dd: M[D]) = ⊛(dd)

    sealed trait ApplicativeBuilder4[D] {
      val d: M[D]

      def apply[E](f: (A, B, C, D) => E)(implicit ap: ApplicFunctor[M]): M[E] = ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d)

      def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D)] = apply(Tuple4.apply)

      def ⊛[E](ee: M[E]) = new ApplicativeBuilder5[E] {
        val e = ee
      }

      def |@|[E](ee: M[E]) = ⊛(ee)

      sealed trait ApplicativeBuilder5[E] {
        val e: M[E]

        def apply[F](f: (A, B, C, D, E) => F)(implicit ap: ApplicFunctor[M]): M[F] = ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e)

        def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E)] = apply(Tuple5.apply)

        def ⊛[F](f: M[F]) = new ApplicativeBuilder6[F] {
          val ff = f
        }

        def |@|[F](f: M[F]) = ⊛(f)

        sealed trait ApplicativeBuilder6[F] {
          val ff: M[F]

          def apply[G](f: (A, B, C, D, E, F) => G)(implicit ap: ApplicFunctor[M]): M[G] = ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff)

          def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F)] = apply(Tuple6.apply)

          def ⊛[G](gg: M[G]) = new ApplicativeBuilder7[G] {
            val g = gg
          }

          def |@|[G](gg: M[G]) = ⊛(gg)

          sealed trait ApplicativeBuilder7[G] {
            val g: M[G]

            def apply[H](f: (A, B, C, D, E, F, G) => H)(implicit ap: ApplicFunctor[M]): M[H] = ap(ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff))(g)

            def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F, G)] = apply(Tuple7.apply)

            def ⊛[H](hh: M[H]) = new ApplicativeBuilder8[H] {
              val h = hh
            }

            def |@|[H](hh: M[H]) = ⊛(hh)

            sealed trait ApplicativeBuilder8[H] {
              val h: M[H]

              def apply[I](f: (A, B, C, D, E, F, G, H) => I)(implicit ap: ApplicFunctor[M]): M[I] = ap(ap(ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff))(g))(h)

              def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F, G, H)] = apply(Tuple8.apply)

              def ⊛[I](ii: M[I]) = new ApplicativeBuilder9[I] {
                val i = ii
              }

              def |@|[I](ii: M[I]) = ⊛(ii)

              sealed trait ApplicativeBuilder9[I] {
                val i: M[I]

                def apply[J](f: (A, B, C, D, E, F, G, H, I) => J)(implicit ap: ApplicFunctor[M]): M[J] =
                  ap(ap(ap(ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff))(g))(h))(i)

                def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F, G, H, I)] = apply(Tuple9.apply)

                def ⊛[J](jj: M[J]) = new ApplicativeBuilder10[J] {
                  val j = jj
                }

                def |@|[J](jj: M[J]) = ⊛(jj)

                sealed trait ApplicativeBuilder10[J] {
                  val j: M[J]

                  def apply[K](f: (A, B, C, D, E, F, G, H, I, J) => K)(implicit ap: ApplicFunctor[M]): M[K] =
                    ap(ap(ap(ap(ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff))(g))(h))(i))(j)

                  def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F, G, H, I, J)] = apply(Tuple10.apply)

                  def ⊛[K](kk: M[K]) = new ApplicativeBuilder11[K] {
                    val k = kk
                  }

                  def |@|[K](kk: M[K]) = ⊛(kk)

                  sealed trait ApplicativeBuilder11[K] {
                    val k: M[K]

                    def apply[L](f: (A, B, C, D, E, F, G, H, I, J, K) => L)(implicit ap: ApplicFunctor[M]): M[L] =
                      ap(ap(ap(ap(ap(ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff))(g))(h))(i))(j))(k)

                    def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F, G, H, I, J, K)] = apply(Tuple11.apply)

                    def ⊛[L](ll: M[L]) = new ApplicativeBuilder12[L] {
                      val l = ll
                    }

                    def |@|[L](ll: M[L]) = ⊛(ll)

                    sealed trait ApplicativeBuilder12[L] {
                      val l: M[L]

                      def apply[MM](f: (A, B, C, D, E, F, G, H, I, J, K, L) => MM)(implicit ap: ApplicFunctor[M]): M[MM] =
                        ap(ap(ap(ap(ap(ap(ap(ap(ap(ap(ap(ap.fmap(f.curried)(a))(b))(c))(d))(e))(ff))(g))(h))(i))(j))(k))(l)

                      def tupled(implicit ap: ApplicFunctor[M]): M[(A, B, C, D, E, F, G, H, I, J, K, L)] = apply(Tuple12.apply)
                    }

                  }

                }

              }

            }

          }

        }

      }

    }

  }

}

// formerly MAB
trait *->*->*[A, F[_, _], B] {
  val value: F[A, B]

  def <<<[C](x: F[C, A])(implicit c: Compose[F]): F[C, B] =
    c.compose(value, x)

  def ⋘[C](x: F[C, A])(implicit c: Compose[F]): F[C, B] =
    <<<(x)

  def >>>[C](x: F[B, C])(implicit c: Compose[F]): F[A, C] =
    c.compose(x, value)

  def ⋙[C](x: F[B, C])(implicit c: Compose[F]): F[A, C] =
    >>>(x)

  def :->[D](g: B => D)(implicit t: Bifunctor[F]): F[A, D] =
    t.rightMap(g)(value)

  def <-:[C](f: A => C)(implicit t: Bifunctor[F]): F[C, B] =
    t.leftMap(f)(value)

  def first[C](implicit a: Arrow[F]): F[(A, C), (B, C)] =
    a.fst(value)

  def second[C](implicit a: Arrow[F]): F[(C, A), (C, B)] =
    a.snd(value)

  def ***[C, D](k: F[C, D])(implicit a: Arrow[F]): F[(A, C), (B, D)] =
    a.split(value)(k)

  def &&&[C](k: F[A, C])(implicit a: Arrow[F]): F[A, (B, C)] =
    a.combine(value)(k)

  def ^>>[C](f: C => A)(implicit a: Arrow[F]): F[C, B] =
    a.mapfst(f)(value)

  def >>^[C](f: B => C)(implicit a: Arrow[F]): F[A, C] =
    a.mapsnd(f)(value)

  def <:>[C](f: A => C)(implicit z: B =:= C, t: Bifunctor[F]): F[C, C] =
    t.bimap(f, z)(value)
}

object *->*->* extends **->**->**

trait **->**->** {
  implicit def **->**->**[A, F[_, _], B](a: F[A, B]): *->*->*[A, F, B] = new *->*->*[A, F, B] {
    val value = a
  }
}