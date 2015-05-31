package provingground.andrewscurtis

import provingground.StringParse._

// Play Json imports
//import play.api.libs.json._
//import play.api.libs.iteratee._
//import play.api.libs.json.Json.toJsFieldJsValueWrapper

/*
 * Free group in n generators
 * An element is represented as a word in integers, together with rank of the corresponding group
 * The negative of a number represents the inverse generator.
 */
object FreeGroups{
  /**
   * String for a letter, e.g. a, a! (for a inverse)
   */
  def letterString(n : Int) = if (n > 0) ('a' + n -1).toChar.toString +"." else ('a' - n -1).toChar.toString+"!."
  /**
   * unicode string for a letter, e.g. "a" or "\bar{a}"
   */
  def letterUnic(n : Int) = if (n > 0) ('a' + n -1).toChar.toString else ('a' - n -1).toChar.toString+ '\u0305'.toString

  /**
   * A word in a free group.
   * @param ls letters of the words represented as integers; 1 represents a, -1 represents a^{-1}
   */
  case class Word(ls: List[Int]) extends AnyVal{
    /**
     * returns reduced form of a word
     */
    def reduce : Word = ls match {
      case x :: y :: zs if x == -y => Word(zs).reduce
      case x :: ys => 
        if (Word(ys).isReduced) 
          x :: Word(ys).reduce
        else (x :: Word(ys).reduce).reduce
      case _ => this
    }

    def isReduced = (this == reduce)
    /**
     * string representation
     */
    def toPlainString = ((ls map (letterString(_))).foldLeft("")(_+_)).dropRight(1)

    override def toString = if (ls.isEmpty) "1" else toUnicode

    /**
     * unicode representation.
     */
    def toUnicode = ((ls map (letterUnic(_))).foldLeft("")(_+_))

    /**
     * letter prepended to word
     */
    def ::(let : Int) = Word(let :: ls)
    
    /**
     * inverse
     */
    def inv = Word(ls.reverse map ((n) => -n))

    /**
     * inverse
     */
    def ! = inv

    /**
     * returns this to kth power.
     */
    def pow : Int => Word = {
      case 0 => Word(List())
      case k if k >0 => Word(List.fill(k)(ls).flatten)
      case k if k <0 => this.inv.pow(-k)
    }

    /**
     * raise to nth power.
     */
    def ^(n: Int) = pow(n)

    /**
     * multiply and reduce
     */
    def *(that: Word) = Word(ls ++ that.ls).reduce

    /**
     * conjugate
     */
    def conj(that: Word) = that.inv * this * that

    /**
     * conjugate
     */
    def ^(that: Word) = conj(that)

    /**
     * conjugate by a generator (or its inverse)
     */
    def conjGen(k: Int) = Word((-k) :: (ls :+ k)).reduce

    /**
     * conjugate by a generator (or its inverse).
     * @param k index of generator, starting at 1.
     */
    def ^^(k: Int)  = conjGen(k)

    /**
     * largest generator in the free group.
     */
    def maxgen = (ls map (_.abs)).max

    /**
     * remove generators of rank and above.
     */
    def rmvtop(rank : Int) = Word (ls filter (_.abs < rank))
  }

  object Word{
    /**
     * helper for fromString
     */
    def listFromChars(s: List[Char]) : List[Int] = {
      s match {
      case Nil => List()
      case x :: '\u0305' :: tail =>
         (-(x - 'a' + 1)) :: listFromChars(tail)                  
      case x :: tail =>
        (x - 'a' + 1) :: listFromChars(tail)      
    }
    }
    
    /**
     * word from a string.
     */
    def fromString(s: String): Word = if (s=="1") Word(List()) 
      else Word(listFromChars(s.replace("!", "\u0305").replace(" ", "").replace(".", "").toList))
    
    /**
     * word from a string.
     */
    def apply(w: String) = fromString(w)
    
    /**
     * the identity
     */
    val e = Word(List())
  }

  /**
   * weight of a word, for a generation process where we extend with some probability, picking letters at random.
   */
  def wordWeight(w : Word, wrdCntn: Double, rank : Double) = (1 - wrdCntn) * math.pow(wrdCntn / (2 * rank), w.ls.length)

  /*
   * Objects involved in Andrews-Curtis evolution : Presentations and Moves
   */
//  trait ACobject

  /**
   * Finite presentation of a group
   * 
   * @param rels relations
   * @param rank number of generators.
   */
  case class Presentation(rels : List[Word], rank : Int){
    /**
     * number of relations
     */
    val sz = rels.length

    /**
     * string without  unicode.
     */
    def toPlainString = {
      val gens = (for (j <- 0 to rank-1) yield ('a'+j).toChar.toString).toList.mkString(",")
      val relstring = (for (rel <- rels) yield rel.toPlainString).toList.mkString(",")
      s"<$gens; $relstring>"
    }

    /**
     * unicode string
     */
    def toUnicode = {
      val gens = (for (j <- 0 to rank-1) yield ('a'+j).toChar.toString).toList.mkString(",")
      val relstring = (for (rel <- rels) yield rel.toUnicode).toList.mkString(",")
      s"<$gens; $relstring>"
    }

    /**
     * unicode string
     */
    override def toString = toUnicode

    /**
     * defect of the presentation.
     */
    val defect = rank - sz

    /**
     * largest generator appearing in relation.
     */
    def maxgen = (rels map (_.maxgen)).max

    /**
     * returns presentation with ith element inverted.
     */
    def inv (k : Int) = {
      val result = (0 to sz -1) map {(i) => if (i == k) rels(i).inv else rels(i)}
      Presentation(result.toList, rank)
    }

    /**
     * presentation with kth relation multiplied on the right by the ith relation.
     */
    def rtmult (k : Int, l : Int) = {
      val result = (0 to sz -1) map {(i) => if (i == k) rels(k) * rels(l) else rels(i)}
      Presentation(result.toList, rank)
    }

    /**
     * presentation with kth relation multiplied on the right by the ith relation.
     */
    def lftmult (k : Int, l : Int) = {
      val result = (0 to sz -1) map {(i) => if (i == k) rels(l) * rels(k) else rels(i)}
      Presentation(result.toList, rank)
    }

    /**
     * presentation with kth relation conjugated by generator with index l.
     */
    def conj (k : Int, l : Int) = {
      val result = (0 to sz -1) map {(i) => if (i == k) rels(k)^^l else rels(i)}
      Presentation(result.toList, rank)
    }

    /**
     * Andrews-Curtis stabilization
     */
    def ACstab = Presentation(Word(List(rank+1)) :: rels, rank +1)

    /**
     * Tietze stabilization.
     */
    def ttzStab = Presentation(Word(List()) :: rels, rank)

    /**
     * returns whether Andrews-Curtis stabilized.
     */
    def ACstabilized = rels contains (Word(List(rank+1)))

    /**
     * (unsafe) Andrews-Curtis destabilization.
     */
    def ACdestab = {
      val newrels = rels filter ((w : Word) => w != Word(List(rank+1))) map (_.rmvtop(rank))
      Presentation(newrels, rank -1)
    }
/*
    def toJson = {
      val listlist = rels map (_.ls)
      Json.obj("rank" -> rank, "words" -> listlist)
    }*/
  }
  
  object Presentation{
    /** 
     *  parses string to a presentation.
     */
    def fromString(s: String) = {
      val ss = s.replaceAll("[ <>]", "")
      val genWord :: relWord :: _ = ss.split(";").toList
      val rank = genWord.split(",").length
      val rels = relWord.split(",") map (Word.fromString(_))
      Presentation(rels.toList, rank)
    }
    
    /**
     * gives presentation from rank and strings for words.
     */
    def apply(rank: Int, ws: String*) : Presentation = 
      Presentation(ws.toList map (Word.fromString), rank)
      
    val empty = Presentation(List(), 0)
    
    def trivial(n: Int) = 
      Presentation((1 to n).toList map ((i) => Word(List(i))), n)
  }
  
  def presentationWeight(pres : Presentation, presCntn : Double, wrdCntn : Double) = {
    val wordwts = pres.rels map (wordWeight(_, wrdCntn, pres.rank))
    (1 - presCntn) * math.pow(presCntn, pres.sz) * ((wordwts :\ 1.0)(_ * _))
  }

  /*
   * Empty presentation
   */
  val nullpres = Presentation(List(), 0)

  implicit def writeWord = WriteString.simple[Word]
  
  implicit def writePres = WriteString.simple[Presentation]
  
  implicit def readWord = ReadString(Word.fromString)
  
  implicit def readPres = ReadString(Presentation.fromString)
}
