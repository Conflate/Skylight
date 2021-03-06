# Wayland McWayface (JVM-edition)

This is an implementation of Wayland McWayface [Part 3](https://drewdevault.com/2018/02/28/Writing-a-wayland-compositor-part-3.html) by Drew DeVault. 
In order to have bindings of wlroots and wayland within java, I've made use of the jextract utility provided by project panama. 
I've also made use of the `foreign` api directly which can be seen in `usr.include.stdlib` in the scala sources.

## Dependencies

To build and run this project, you will require [build ea+44 of project panama on jdk 13](https://jdk.java.net/panama/).
The following will be required as well: the wayland development libraries, the wlroots library and development libraries, clang, libpixman's development libraries, libxkbcommon and possibly others. 

## Building

To build this project, run `sbt compile` in the root of the project directory. 
If the build fails, Please check to make sure the following settings in build.sbt point to your xdg-shell-unstable-v6.xml, your include path, and the path that contains the .so files on your system.
```scala
xdgShellProtocolLocation := file("/usr/share/wayland-protocols/unstable/xdg-shell/xdg-shell-unstable-v6.xml")

includeDirectory := file("/usr/include")

libraryDirectory := file("/usr/lib64")
```

## Implementation

I have implemented the Wayland McWayface part 1 demo as written [here](https://github.com/ddevault/mcwayface/blob/f89092e7d38e43c55583098beadde26b3d1235eb/src/main.c) for the most part. Below are the list of changes I have made.


* I did not use `wl_container_of` in a few places where the original demo did. Instead of 
  ```c
  static void output_frame_notify(struct wl_listener *listener, void *data) {
	   struct mcw_output *output = wl_container_of(listener, output, frame);
  ```
  I've written
  ```scala
  lazy val output_frame_notify: FI5 = (_: Pointer[wl_listener], data: Pointer[_]) => {
  ```
  because scala has closures and mcw_output would have to become a C struct in order to retrieve it from the listener embedded in it.

* `mcw_output` does not have a `link` member cause we store it in `outputs: Set[mcw_output]` within `mcw_server` instead a `wl_list`.

* I use constructors for `mcw_server` and `mcw_output` instead of treating them as structs.

* Because of an issue with the bytecode emitted with jextract, some anonymous struct bindings are not comprehensible to scala
(such as `wlroots.backend.anon$backend_h$444`). Because of this, I've created java classes to handle the extraction of these 
anonymous struct types from their parent structs. wlr_output_workaround exists for much the same reason.

  I should be able to remove these workarounds in a future version of project panama's jdk builds 

* I've implemented `wl_signal_add` in my code because it is inlined in the original library. 
* I've implemented `wl_container_of` in my code because it's a macro in the original wayland library.
* There are a few api changes from the original tutorial because the wlroots api has changed between version 0.2 (which was used in Drew DeVault's tutorial) and version 0.3. This project targets wlroots version 0.3


## Notes

In part 3 it shows using gnome-terminal with wayland mcwayface. I could not get gnome-terminal working, but weston-terminal works just fine: 

![Imgur](https://i.imgur.com/1T03xi5.png)

Also, I've added `wl_display_terminate` to the `output_destroy_notify` callback in order to have the WindowManager close when the display is closed.
