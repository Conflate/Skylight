package io.github.markehammons

import java.foreign.layout.Group
import java.foreign.memory.{LayoutType, Pointer, Struct}
import java.foreign.{NativeTypes, Scope}
import java.io.{ByteArrayOutputStream, PrintWriter}
import java.util.spi.ToolProvider

import usr.include.wayland.wayland_server_core.{FI5, wl_listener, wl_signal}
import usr.include.wayland.wayland_server_core_h.{wl_display_add_socket_auto, wl_display_destroy, wl_display_run, wl_display_terminate, wl_display_init_shm}
import usr.include.wayland.wayland_util.wl_list
import usr.include.wayland.wayland_util_h.{wl_list_insert, wl_list_length, wl_list_remove}
import wlroots.backend_h.{wlr_backend_get_renderer, wlr_backend_start}
import wlroots.wlr_output.{wlr_output, wlr_output_mode}
import wlroots.wlr_output_h.{wlr_output_make_current, wlr_output_set_mode, wlr_output_create_global}
import wlroots.wlr_renderer_h.{wlr_renderer_begin, wlr_renderer_clear, wlr_renderer_end}
import wlroots.wlr_screenshooter_h.wlr_screenshooter_create
import wlroots.wlr_idle_h.wlr_idle_create
import wlroots.wlr_primary_selection_v1_h.wlr_primary_selection_v1_device_manager_create
import wlroots.wlr_gamma_control_h.wlr_gamma_control_manager_create

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import implicits._
import usr.include.stdint
import usr.include.stdlib

import scala.annotation.tailrec

object main {
  type Listable[T] = {
    def ptr(): Pointer[T]
    def link$ptr(): Pointer[wl_list]
  }

  def offsetOf[T <: Struct[T]](fieldName: String)(implicit classTag: ClassTag[T]) = {
    val g = LayoutType.ofStruct(classTag.runtimeClass.asInstanceOf[Class[T]]).layout().asInstanceOf[Group]

    val bits = for(l <- g.elements().asScala.takeWhile(_.name().filter(_ == fieldName).isEmpty)) yield {
      l.bitsSize()
    }

    bits.sum / 8
  }

  @tailrec
  def bytePointerToString(p: Pointer[java.lang.Byte], length: Int = 0): String = {
    if(p.isNull || p.offset(length).get() == 0) {
      val arr = p.withSize(length).toArray[Array[Byte]](l => Array.ofDim[Byte](l))
      arr.map(_.toChar).mkString
    } else {
      bytePointerToString(p, length + 1)
    }
  }

  def runTool(name: String, arguments: String*): Either[String,String] = {
    println(System.getProperty("jextract.debug"))
    println(arguments.mkString(" "))

    val maybeTool: Option[ToolProvider] = {
      val _tool = ToolProvider.findFirst(name)
      if(_tool.isPresent) {
        Some(_tool.get())
      } else {
        None
      }
    }

    val result = for(tool <- maybeTool) yield {
      println(s"running ${tool.name()}")
      val stdOut = new ByteArrayOutputStream()
      val errOut = new ByteArrayOutputStream()
      val code = tool.run(new PrintWriter(System.out), new PrintWriter(System.err), arguments: _*)
      (code, new String(stdOut.toByteArray), new String(errOut.toByteArray))
    }

    result
      .toRight(s"Could not find tool $name in your java development environment")
      .flatMap{ case (code,ret,err) =>
        if(ret.contains("Error:") || err.nonEmpty || code != 0) {
          Left(s"failure with code $code: ${ret + err}")
        } else {
          println(s"return value: $ret")
          println(s"error value: $ret")
          Right(ret -> "")
        }
      }
      .map(_._1)
  }

  runTool("jextract",
    "/usr/include/wlr/types/wlr_output.h",
    "/usr/include/wlr/backend.h",
    "/usr/include/wlr/render/wlr_renderer.h",
    "-m", "/usr/include/wlr/backend=wlroots.backend_headers",
    "-m", "/usr/include/bits/types=usr.include.type_headers",
    "-C", "\"-DWLR_USE_UNSTABLE\"",
    "-I", "/usr/include/wlr",
    "-I", "/usr/include/wayland/",
    "-I", "/usr/include/pixman-1/",
    "-L", "/usr/lib64/",
    "--record-library-path",
    "-l", "wlroots",
    "-t", "wlroots",
    "-o", "wlroots-test.jar")



  def extractAnonStruct[T,U](t: T)(implicit anonExtractable: HasExtractableEvents[T, U]) = anonExtractable.extractFrom(t)

  def wl_signal_add(signal: Pointer[wl_signal], listener: Pointer[wl_listener]) = wl_list_insert(signal.get().listener_list$get().prev$get(), listener.get().link$ptr())

  def wl_container_of[T <: Listable[T] with Struct[T]](listItem: wl_list)(implicit classTag: ClassTag[T]): Pointer[T] = {
    val clazz = classTag.runtimeClass.asInstanceOf[Class[T]]
    val offset = offsetOf[T]("link")
    listItem.ptr().cast(NativeTypes.VOID).cast(NativeTypes.INT8).offset(offset).cast(NativeTypes.VOID).cast(LayoutType.ofStruct(clazz))
  }

  def wl_container_of[T <: Listable[T] with Struct[T]](listItemPtr: Pointer[wl_list])(implicit classTag: ClassTag[T]): Pointer[T] = {
    wl_container_of(listItemPtr.get)
  }


  def output_frame_notify(output: mcw_output): FI5 = (_: Pointer[wl_listener], data: Pointer[_]) => {
    import output.{color, dec}

    val wlr_output = data.cast(LayoutType.ofStruct(classOf[wlr_output]))
    val renderer = wlr_backend_get_renderer(
      wlr_output.get().backend$get())

    val now = System.currentTimeMillis()

    // Calculate a color, just for pretty demo purposes
    val ms = now - output.last_frame
    val inc = (dec + 1) % 3
    val adjust = ms / 2000f
    color.set(inc, color.get(inc) + adjust)
    color.set(dec, color.get(dec) - adjust)

    if(color.get(dec) < 0f) {
      color.set(inc, 1.0f)
      color.set(dec, 0.0f)
      dec = inc
    }
    // End pretty color calculation


    wlr_output_make_current(wlr_output, Pointer.ofNull())
    wlr_renderer_begin(renderer, wlr_output.get().width$get(), wlr_output.get().height$get())

    wlr_renderer_clear(renderer, output.color.elementPointer())

    wlr_output_workaround.swap_buffers(wlr_output)
    wlr_renderer_end(renderer)

    output.last_frame = now
  }

  def output_destroy_notify(output: mcw_output): FI5 = (_: Pointer[wl_listener], _: Pointer[_]) => {
    wl_list_remove(output.destroy.link$ptr())
    wl_list_remove(output.frame.link$ptr())
    wl_display_terminate(output.server.wl_display)
    output.free()
  }

  def new_output_notify(server: mcw_server, scope: Scope): FI5 = (_: Pointer[wl_listener], data: Pointer[_]) => {
    val wlr_output = data.cast(LayoutType.ofStruct(classOf[wlr_output]))

    if(wl_list_length(wlr_output.get().modes$ptr()) > 0) {
      val mode =
        wl_container_of[wlr_output_mode](wlr_output.get().modes$get().prev$get().get())
      wlr_output_set_mode(wlr_output, mode)
    }

    val output = mcw_output(wlr_output.get(), server, scope.fork())

    output.destroy.notify$set(scope.allocateCallback(output_destroy_notify(output)))
    wl_signal_add(extractAnonStruct(wlr_output.get()).destroy$ptr(), output.destroy.ptr())
    output.frame.notify$set(scope.allocateCallback(output_frame_notify(output)))
    wl_signal_add(extractAnonStruct(wlr_output.get()).frame$ptr(), output.frame.ptr())

    wlr_output_create_global(wlr_output)
  }


  def main(args: Array[String]) = {
    val scope = Scope.globalScope()

    val server = mcw_server(scope)
    require(!server.wl_event_loop.isNull)

    server.new_output.notify$set(scope.allocateCallback(new_output_notify(server, scope)))
    wl_signal_add(extractAnonStruct(server.backend).new_output$ptr(), server.new_output.ptr())

    val socket = bytePointerToString(wl_display_add_socket_auto(server.wl_display))
    require(socket != "")

    println(s"Running compositor on wayland display $socket")

    stdlib.setenv("WAYLAND_DISPLAY", socket, true)

    wl_display_init_shm(server.wl_display)
    wlr_gamma_control_manager_create(server.wl_display)
    wlr_screenshooter_create(server.wl_display)
    wlr_primary_selection_v1_device_manager_create(server.wl_display)
    wlr_idle_create(server.wl_display)

    if(!wlr_backend_start(server.backend)) {
      System.err.println("failed to start backend!")
      wl_display_destroy(server.wl_display)
    } else {
      wl_display_run(server.wl_display)
      wl_display_destroy(server.wl_display)
    }
  }
}