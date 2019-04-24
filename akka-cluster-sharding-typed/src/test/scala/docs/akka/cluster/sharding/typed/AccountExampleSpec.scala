/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.akka.cluster.sharding.typed

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.typed.Cluster
import akka.cluster.typed.Join
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpecLike

object AccountExampleSpec {
  val config = ConfigFactory.parseString("""
      akka.actor.provider = cluster

      akka.remote.netty.tcp.port = 0
      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    """)

}

class AccountExampleSpec extends ScalaTestWithActorTestKit(AccountExampleSpec.config) with WordSpecLike {
  import AccountExampleWithEventHandlersInState.AccountEntity
  import AccountExampleWithEventHandlersInState.AccountEntity._

  private val sharding = ClusterSharding(system)

  override def beforeAll(): Unit = {
    super.beforeAll()
    Cluster(system).manager ! Join(Cluster(system).selfMember.address)

    // FIXME use EventSourcedEntity.withEnforcedReplies when https://github.com/akka/akka/pull/26692 has been merged
    sharding.init(Entity(AccountEntity.entityTypeKey, ctx => AccountEntity.behavior(ctx.entityId)))
  }

  "Account example" must {

    "handle Deposit" in {
      val probe = createTestProbe[OperationResult]()
      val ref = ClusterSharding(system).entityRefFor(AccountEntity.entityTypeKey, "1")
      ref ! CreateAccount(probe.ref)
      probe.expectMessage(Confirmed)
      ref ! Deposit(100, probe.ref)
      probe.expectMessage(Confirmed)
      ref ! Deposit(10, probe.ref)
      probe.expectMessage(Confirmed)
    }

    "handle Withdraw" in {
      val probe = createTestProbe[OperationResult]()
      val ref = ClusterSharding(system).entityRefFor(AccountEntity.entityTypeKey, "2")
      ref ! CreateAccount(probe.ref)
      probe.expectMessage(Confirmed)
      ref ! Deposit(100, probe.ref)
      probe.expectMessage(Confirmed)
      ref ! Withdraw(10, probe.ref)
      probe.expectMessage(Confirmed)
    }

    "reject Withdraw overdraft" in {
      val probe = createTestProbe[OperationResult]()
      val ref = ClusterSharding(system).entityRefFor(AccountEntity.entityTypeKey, "3")
      ref ! CreateAccount(probe.ref)
      probe.expectMessage(Confirmed)
      ref ! Deposit(100, probe.ref)
      probe.expectMessage(Confirmed)
      ref ! Withdraw(110, probe.ref)
      probe.expectMessageType[Rejected]
    }

    "handle GetBalance" in {
      val opProbe = createTestProbe[OperationResult]()
      val ref = ClusterSharding(system).entityRefFor(AccountEntity.entityTypeKey, "4")
      ref ! CreateAccount(opProbe.ref)
      opProbe.expectMessage(Confirmed)
      ref ! Deposit(100, opProbe.ref)
      opProbe.expectMessage(Confirmed)

      val getProbe = createTestProbe[CurrentBalance]()
      ref ! GetBalance(getProbe.ref)
      getProbe.expectMessage(CurrentBalance(100))
    }

    "be usable with ask" in {
      val ref = ClusterSharding(system).entityRefFor(AccountEntity.entityTypeKey, "5")
      val createResult: Future[OperationResult] = ref.ask(CreateAccount(_))
      createResult.futureValue should ===(Confirmed)
      implicit val ec: ExecutionContext = testKit.system.executionContext

      // errors are shown in IntelliJ Scala plugin 2019.1.6, but compiles with Scala 2.12.8
      ref.ask(Deposit(100, _)).futureValue should ===(Confirmed)
      ref.ask(Withdraw(10, _)).futureValue should ===(Confirmed)
      ref.ask(GetBalance(_)).map(_.balance).futureValue should ===(90)
    }

  }
}
