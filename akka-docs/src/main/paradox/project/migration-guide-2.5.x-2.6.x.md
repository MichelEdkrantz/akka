# Migration Guide 2.5.x to 2.6.x

## akka-camel removed

After being deprecated in 2.5.0, the akka-camel module has been removed in 2.6.
As an alternative we recommend [Alpakka](https://doc.akka.io/docs/alpakka/current/).

This is of course not a drop-in replacement. If there is community interest we
are open to setting up akka-camel as a separate community-maintained
repository.

## akka-agent removed

After being deprecated in 2.5.0, the akka-agent module has been removed in 2.6.
If there is interest it may be moved to a separate, community-maintained
repository.

## Scala 2.11 no longer supported

If you are still using Scala 2.11 then you must upgrade to 2.12 or 2.13

### Actor DSL removal

Actor DSL is a rarely used feature and has been deprecated since `2.5.0`.
Use plain `system.actorOf` instead of the DSL to create Actors if you have been using it.

### CoordinatedShutdown is run from ActorSystem.terminate

No migration is needed but it is mentioned here because it is a change in behavior.

When `ActorSystem.terminate()` is called @ref:[`CoordinatedShutdown`](../actors.md#coordinated-shutdown)
will be run in Akka 2.6.x, which wasn't the case in 2.5.x. For example, if using Akka Cluster this means that
member will attempt to leave the cluster gracefully.

If this is not desired behavior, for example in tests, you can disable this feature with the following configuration
and then it will behave as in Akka 2.5.x:

```
akka.coordinated-shutdown.run-by-actor-system-terminate = off
```
