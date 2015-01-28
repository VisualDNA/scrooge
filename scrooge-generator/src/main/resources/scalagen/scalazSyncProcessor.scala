package {{package}}

import org.apache.thrift.protocol.TProtocol
import com.twitter.scrooge.ThriftFunction
import com.twitter.scrooge.ThriftProcessor
import com.twitter.scrooge.ThriftStruct
import scalaz.concurrent.Task
import akka.actor.ActorSystem

{{docstring}}
@javax.annotation.Generated(value = Array("com.twitter.scrooge.Compiler"), date = "{{date}}")
class {{ServiceName}}$ScalazSyncProcessor(iface: {{ServiceName}}[Task]) extends ThriftProcessor[{{ServiceName}}[Task]](iface) {

  protected val processMap = (
{{#syncFunctions}}
      "{{funcName}}" -> Fn${{funcName}} ::
{{/syncFunctions}}
      Nil
  ).toMap[String, ThriftFunction[{{ServiceName}}[Task], _ <: ThriftStruct]]

{{#syncFunctions}}
  object Fn${{funcName}} extends ThriftFunction[{{ServiceName}}[Task], {{ServiceName}}.{{funcName}}$args]("{{funcName}}") {

    def decode(in: TProtocol) = {
      {{ServiceName}}.{{funcName}}$args.decode(in)
    }

    def getResult(iface: {{ServiceName}}[Task], args: {{ServiceName}}.{{funcName}}$args) = 
{{#hasThrows}}
      try {
{{/hasThrows}}
        iface.{{funcName}}({{argNames}}).run
{{#hasThrows}}
      } catch {
{{#throws}}
          case e: {{typeName}} => {{ServiceName}}.{{funcName}}$result({{fieldName}} = Some(e))
{{/throws}}
        }
{{/hasThrows}}
    }

{{/syncFunctions}}
}
