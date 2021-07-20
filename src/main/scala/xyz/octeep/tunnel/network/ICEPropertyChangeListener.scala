package xyz.octeep.tunnel.network

import org.ice4j.ice.{Agent, IceProcessingState}

import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}

class ICEPropertyChangeListener(task: Agent => Future[Unit])(implicit ec: ExecutionContext) extends PropertyChangeListener {

  private val monitor = new AtomicBoolean(false)

  override def propertyChange(evt: PropertyChangeEvent): Unit =
    evt.getNewValue match {
      case IceProcessingState.COMPLETED =>
        Future {
          monitor.synchronized(monitor.wait(10000L))
        }.flatMap { _ =>
          if (monitor.get()) task(evt.getSource.asInstanceOf[Agent]) else Future.unit
        }
      case IceProcessingState.TERMINATED =>
        monitor.synchronized {
          monitor.set(true)
          monitor.notifyAll()
        }
      case IceProcessingState.FAILED =>
        monitor.synchronized(monitor.notifyAll())
      case _ =>
    }

}
