/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.event.Logging
import akka.lease.LeaseSettings
import akka.lease.scaladsl.Lease
import akka.testkit.TestProbe

import scala.concurrent.{ Future, Promise }

object TestLeaseExt extends ExtensionId[TestLeaseExt] with ExtensionIdProvider {
  override def get(system: ActorSystem): TestLeaseExt = super.get(system)
  override def lookup = TestLeaseExt
  override def createExtension(system: ExtendedActorSystem): TestLeaseExt = new TestLeaseExt(system)
}

class TestLeaseExt(val system: ExtendedActorSystem) extends Extension {

  private val testLeases = new ConcurrentHashMap[String, TestLease]()

  def getTestLease(name: String): TestLease = {
    val lease = testLeases.get(name)
    if (lease == null) throw new IllegalStateException(s"Test lease $name has not been set yet")
    lease
  }

  def setTestLease(name: String, lease: TestLease): Unit =
    testLeases.put(name, lease)

}

object TestLease {
  final case class AcquireReq(owner: String)
  final case class ReleaseReq(owner: String)
}

class TestLease(settings: LeaseSettings, system: ExtendedActorSystem) extends Lease(settings) {
  import TestLease._

  val log = Logging(system, getClass)
  val probe = TestProbe()(system)

  log.info("Creating lease {}", settings)

  TestLeaseExt(system).setTestLease(settings.leaseName, this)

  val initialPromise = Promise[Boolean]

  private val nextAcquireResult = new AtomicReference[Future[Boolean]](initialPromise.future)

  def setNextAcquireResult(next: Future[Boolean]): Unit =
    nextAcquireResult.set(next)

  override def acquire(): Future[Boolean] = {
    println("acquire, current response " + nextAcquireResult)
    probe.ref ! AcquireReq(settings.ownerName)
    nextAcquireResult.get()
  }

  override def release(): Future[Boolean] = {
    probe.ref ! ReleaseReq(settings.ownerName)
    Future.successful(true)
  }

  override def checkLease(): Boolean = false
}
