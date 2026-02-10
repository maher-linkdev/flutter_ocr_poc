/// Represents a bounding box around detected text.
///
/// Coordinates are in pixel space relative to the original image.
/// The box is defined by four corner points (top-left, top-right,
/// bottom-right, bottom-left) to support rotated text regions.
class BoundingBox {
  /// Top-left corner [x, y].
  final List<double> topLeft;

  /// Top-right corner [x, y].
  final List<double> topRight;

  /// Bottom-right corner [x, y].
  final List<double> bottomRight;

  /// Bottom-left corner [x, y].
  final List<double> bottomLeft;

  const BoundingBox({
    required this.topLeft,
    required this.topRight,
    required this.bottomRight,
    required this.bottomLeft,
  });

  /// Axis-aligned bounding rect (x, y, width, height).
  double get x => topLeft[0];
  double get y => topLeft[1];
  double get width => topRight[0] - topLeft[0];
  double get height => bottomLeft[1] - topLeft[1];

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is BoundingBox &&
        _listEquals(other.topLeft, topLeft) &&
        _listEquals(other.topRight, topRight) &&
        _listEquals(other.bottomRight, bottomRight) &&
        _listEquals(other.bottomLeft, bottomLeft);
  }

  @override
  int get hashCode =>
      Object.hash(topLeft, topRight, bottomRight, bottomLeft);

  static bool _listEquals(List<double> a, List<double> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }
}
