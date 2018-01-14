package mill.define

import java.lang.reflect.Modifier

import ammonite.main.Router.{EntryPoint, Overrides}
import ammonite.ops.Path

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/**
  * `Module` is a class meant to be extended by `trait`s *only*, in order to
  * propagate the implicit parameters forward to the final concrete
  * instantiation site so they can capture the enclosing/line information of
  * the concrete instance.
  */
class Module(implicit ctx0: mill.define.Ctx) extends mill.moduledefs.Cacher{
  def commands: Seq[EntryPoint[Module]] = ???

  def traverse[T](f: (Module, Segments) => Seq[T]): Seq[T] = {
    ???
  }

  lazy val segmentsToModules = traverse{(m, s) => Seq(s -> m)}
    .toMap

  lazy val modules = segmentsToModules.valuesIterator.toSet
  lazy val segmentsToTargets = traverse{(m, s) => m.reflect[Target[_]]}
    .map(t => (t.ctx.segments, t))
    .toMap

  lazy val targets = segmentsToTargets.valuesIterator.toSet
  lazy val segmentsToCommands = traverse{
    (m, s) => m.commands.map(e => s ++ Seq(Segment.Label(e.name)) -> e)
  }.toMap

  def ctx = ctx0
  // Ensure we do not propagate the implicit parameters as implicits within
  // the body of any inheriting class/trait/objects, as it would screw up any
  // one else trying to use sourcecode.{Enclosing,Line} to capture debug info
  val millModuleEnclosing = ctx.enclosing
  val millModuleLine = ctx.lineNum
  def basePath: Path = ctx.basePath / (ctx.segment match{
    case Segment.Label(s) => List(s)
    case Segment.Cross(vs) => vs.map(_.toString)
  })
  implicit def millModuleBasePath: BasePath = BasePath(basePath)
  implicit def millModuleSegments: Segments = {
    ctx.segments0 ++ Seq(ctx.segment)
  }
  def reflect[T: ClassTag] = {
    this
      .getClass
      .getMethods
      .filter(_.getParameterCount == 0)
      .filter(x => (x.getModifiers & Modifier.STATIC) == 0)
      .filter(implicitly[ClassTag[T]].runtimeClass isAssignableFrom _.getReturnType)
      .map(_.invoke(this).asInstanceOf[T])
  }
}
trait TaskModule extends Module {
  def defaultCommandName(): String
}

class BaseModule(basePath: Path)
                (implicit millModuleEnclosing0: sourcecode.Enclosing,
                 millModuleLine0: sourcecode.Line,
                 millName0: sourcecode.Name,
                 overrides0: Overrides)
  extends Module()(
    mill.define.Ctx.make(implicitly, implicitly, implicitly, BasePath(basePath), Segments(), implicitly)
  )