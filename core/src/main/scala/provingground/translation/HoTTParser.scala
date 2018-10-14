package provingground.translation

import provingground._
import HoTT._
import HoTTParser._
import fastparse.all
import monix.eval.Task

import scala.collection.immutable

object HoTTParser {
  sealed trait Stat

  case class Expr(term: Term) extends Stat

  case class Defn(name: String, value: Term) extends Stat

  case class Block(stats: Vector[Stat]) {
    def +:(s: Stat) = Block(s +: stats)

    def valueOpt: Option[Term] =
      stats.lastOption.map {
        case Expr(t)    => t
        case Defn(_, t) => t
      }
  }
}

case class HoTTParser(ctx: Context) { self =>
  import ctx.namedTerms
  import fastparse._
  val White: WhitespaceApi.Wrapper = WhitespaceApi.Wrapper {
    import fastparse.all._
    NoTrace(" ".rep)
  }
  import fastparse.noApi._
  import White._

  def +(n: String, t: Term) = HoTTParser(ctx.defineSym(Name(n), t))

  def +(dfn: Defn) = HoTTParser(ctx.defineSym(Name(dfn.name), dfn.value))

  val predefs: P[Term] =
    P("Type").map((_) => Type: Term) |
      P("Star").map((_) => Star: Term) |
      P("Unit").map((_) => Unit: Term) |
      P("Zero").map((_) => Zero: Term) |
      P("Prop").map((_) => Prop: Term)

  val named: P[Term] =
    namedTerms.foldRight[P[Term]](predefs) {
      case ((name, term), parser) => P(name).map((_) => term) | parser
    }

  val alphachar
    : immutable.IndexedSeq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ Seq('$',
                                                                       '@',
                                                                       '_')

  val alphanum: immutable.IndexedSeq[Char] = alphachar ++ ('0' to '9') ++ Seq(
    '.')

  val str: core.Parser[String, Char, String] =
    (P(CharIn(alphachar).!) ~ P(CharIn(alphanum).rep.!)).map {
      case (h, t) => h + t.toString
    }

  val name: P[String] =
    P("\"" ~ str ~ "\"")

  val symbolic: P[Term] = P(name ~ P("::") ~ term).map {
    case (s, t) => s :: toTyp(t)
  }

  val parens: P[Term] = P("(" ~ term ~ ")")

  val simpleterm: P[Term] = P(parens | named)

  val lmbdaP: P[Term] =
    (P(
      "lmbda" ~ "(" ~ term ~ ")" ~ "(" ~ term ~ ")"
    ) |
      P(
        simpleterm ~ ":->" ~ term
      )).map { case (x, y) => lmbda(x)(y) }

  val lambdaP: P[Term] =
    (P(
      "lambda" ~ "(" ~ term ~ ")" ~ "(" ~ term ~ ")"
    ) |
      P(
        simpleterm ~ ":~>" ~ term
      )).map { case (x, y) => lmbda(x)(y) }

  val funcTyp = P(
    simpleterm ~ "->:" ~ term
  ).map { case (x, y) => toTyp(x) ->: toTyp(y) }

  val piTyp = P(
    simpleterm ~ "~>:" ~ term
  ).map { case (x, y) => x ~>: toTyp(y) }

  val applnP: core.Parser[Term, Char, String] =
    P(simpleterm ~ "(" ~ term ~ ")").map {
      case (f, x) => applyFunc(f, x)
    }

  val term: P[Term] = P(
    symbolic | lmbdaP | lambdaP | applnP | funcTyp | piTyp | simpleterm)

  val break
    : core.Parser[Unit, Char, String] = P(spc ~ (End | CharIn("\n;"))) | P(
    "//" ~ CharPred(_ != '\n').rep ~ ("\n" | End))

  val spc: all.P[Unit] = CharIn(" \t").rep

  val defn: core.Parser[Defn, Char, String] =
    P(spc ~ "val" ~ str ~ "=" ~ term ~ break).map {
      case (n, t) => Defn(n, t)
    }

  val expr: core.Parser[Expr, Char, String] = (spc ~ term ~ break).map(Expr)

  val stat: P[Stat] = defn | expr

  val block: P[Block] =
    P(spc ~ "//" ~ CharPred(_ != '\n').rep ~ "\n" ~ block) |
      (spc ~ "//" ~ CharPred(_ != '\n').rep ~ End).map((_) => Block(Vector())) |
      (spc ~ End).map((_) => Block(Vector())) |
      defn.flatMap((dfn) =>
        (self + dfn).block.map { (tail) =>
          dfn +: tail
      }) |
      P(stat ~ block ~ End).map { case (s, v) => s +: v } |
      P(spc ~ "\n" ~ block)

  val context: P[Context] =
    P(spc ~ "//" ~ CharPred(_ != '\n').rep ~ "\n" ~ context) |
      (spc ~ "//" ~ CharPred(_ != '\n').rep ~ End).map((_) => Context.Empty) |
      (spc ~ End).map((_) => Context.Empty ) |
      defn.flatMap((dfn) =>
        (self + dfn).context.map { (tail) =>
          tail.defineSym(Name(dfn.name), dfn.value)
        }) |
      P(defn ~ context ~ End).map { case (dfn, ct) => ct.defineSym(Name(dfn.name), dfn.value)} |
      P(expr ~ context ~ End).map { case (exp, ct) => ct.introduce(exp.term)} |
      P(spc ~ "\n" ~ context)

}
