package examples.actors

/*
Adapted from PingPongEx.scala, itself an adaption of an example
from the scala distribution (added timing measurement).

This example creates n tasks and has each send a message to the
others. It sends the same structure (and number of messages) as the
kilim/bench/BigPingPong example.
*/
import scala.actors._
import scala.actors.Actor._

object BigPingPong {
  var beginTime: long = 0

  def main(args : Array[String]): Unit = {
    val nTasks = Integer.parseInt(args(0))
    var i = nTasks
    var tasks: Array[Ping] = new Array[Ping](nTasks)
    Scheduler.impl = new SingleThreadedScheduler
    val cTask = new Collector(nTasks);
    cTask.start()

    beginTime = System.currentTimeMillis()
    for (val i <- 0 to nTasks-1) {
      tasks(i) = new Ping(i)
    }
    for (val t <- tasks) {
      t.setOthers(tasks, cTask)
      t.start();
    }
  }
}

abstract class Msg
case class PingMsg(from :int ) extends Msg

class Ping(i: int) extends Actor {
  var id : int = i
  var tasks: Array[Ping] = Array()
  var ctask : Collector = null

  def setOthers(others: Array[Ping], ct : Collector) {
    ctask = ct
    tasks = others
  }

  def act(): unit = {
    System.out.println("" + id + " n = " + tasks.length);
    for (val t <- tasks) {
      if (t != this) {
	t ! new PingMsg(id)
	receive {
	  case PingMsg(from) => { 
	    System.out.println ("" + from + " -> " + id);
	  }
	}
      }
    }
    ctask ! new PingMsg(id)    
  }
}


class Collector(n : int) extends Actor {
  val nTasks = n

  def act(): unit = {
    for (val i <- 1 to nTasks) {
      receive {
	case PingMsg(from) => {
	  //System.out.println("Completed: " + from);
	}
      }
    }

    val elapsed = System.currentTimeMillis() - BigPingPong.beginTime;
    System.out.println("Elapsed  : " + elapsed)
    System.exit(0);
  }
}
