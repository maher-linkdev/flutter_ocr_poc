/// Represents the source from which an image is obtained.
enum ImageSourceType {
  /// Capture a photo using the device camera.
  camera,

  /// Pick an image from the device gallery.
  gallery,

  /// Pick a file from the file system.
  fileSystem,
}
