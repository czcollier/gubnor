package com.shw.gubnor
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.collection.mutable
/**
  * This code shamelessly stolen from here:
  * https://github.com/mauricio/scala-sandbox/blob/master/src/main/scala/trie/Trie.scala
  * http://mauricio.github.io/2015/01/06/building-a-prefix-tree-in-scala.html
  */
object PrefixTrie {

  object Trie {
    def apply() : Trie = new TrieNode()
  }

  sealed trait Trie extends Traversable[String] {
    def append(key: String)
    def findByPrefix(prefix: String): Seq[String]
    def findPrefixesOf(word: String): Seq[String]
    def contains(word: String): Boolean
    def remove(word: String): Boolean
  }

  private class TrieNode(
    val char : Option[Char] = None,
    var word: Option[String] = None) extends Trie {

    private val children = mutable.Map[Char, TrieNode]()

    override def append(key: String) = {

      @tailrec def appendHelper(node: TrieNode, currentIndex: Int): Unit = {
        if (currentIndex == key.length) {
          node.word = Some(key)
        } else {
          val char = key.charAt(currentIndex).toLower
          val result = node.children.getOrElseUpdate(char, { new TrieNode(Some(char)) })

          appendHelper(result, currentIndex + 1)
        }
      }

      appendHelper(this, 0)
    }

    override def foreach[U](f: String => U): Unit = {

      @tailrec def foreachHelper(nodes: TrieNode*): Unit = {
        if (nodes.size != 0) {
          nodes.foreach(node => node.word.foreach(f))
          foreachHelper(nodes.flatMap(node => node.children.values): _*)
        }
      }

      foreachHelper(this)
    }
    override def findByPrefix(prefix: String): scala.collection.Seq[String] = {
      @tailrec def helper(currentIndex: Int, node: TrieNode, items: ListBuffer[String]): ListBuffer[String] = {
        if (currentIndex == prefix.length) {
          items ++ node
        } else {
          node.children.get(prefix.charAt(currentIndex).toLower) match {
            case Some(child) => helper(currentIndex + 1, child, items)
            case None => items
          }
        }
      }

      helper(0, this, new ListBuffer[String]())
    }

    override def findPrefixesOf(prefix: String): scala.collection.Seq[String] = {
      @tailrec def helper(currentIndex: Int, node: TrieNode, items: ListBuffer[String]): ListBuffer[String] = {
        if (currentIndex == prefix.length) {
          items
        } else {
          node.children.get(prefix.charAt(currentIndex).toLower) match {
            case Some(child) => {
              val next = if (child.word.isDefined) items ++ child.word
               else items

              helper(currentIndex + 1, child, next)
            }
            case None => items
          }
        }
      }
      helper(0, this, new ListBuffer[String]())
    }

    override def contains(word: String): Boolean = {
      @tailrec def helper(currentIndex: Int, node: TrieNode): Boolean = {
        if (currentIndex == word.length) {
          node.word.isDefined
        } else {
          node.children.get(word.charAt(currentIndex).toLower) match {
            case Some(child) => helper(currentIndex + 1, child)
            case None => false
          }
        }
      }
      helper(0, this)
    }

    private def pathTo( word : String ) : Option[ListBuffer[TrieNode]] = {
      def helper(buffer : ListBuffer[TrieNode], currentIndex : Int, node : TrieNode) : Option[ListBuffer[TrieNode]] = {
        if ( currentIndex == word.length) {
          node.word.map( word => buffer += node )
        } else {
          node.children.get(word.charAt(currentIndex).toLower) match {
            case Some(found) => {
              buffer += node
              helper(buffer, currentIndex + 1, found)
            }
            case None => None
          }
        }
      }
      helper(new ListBuffer[TrieNode](), 0, this)
    }

    override def remove(word : String) : Boolean = {
      pathTo(word) match {
        case Some(path) => {
          var index = path.length - 1
          var continue = true
          path(index).word = None
          while ( index > 0 && continue ) {
            val current = path(index)
            if (current.word.isDefined) {
              continue = false
            } else {
              val parent = path(index - 1)
              if (current.children.isEmpty) {
                parent.children.remove(word.charAt(index - 1).toLower)
              }
              index -= 1
            }
          }
          true
        }
        case None => false
      }
    }
  }
}
