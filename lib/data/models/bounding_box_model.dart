/// Data transfer object for bounding box data from the native layer.
///
/// Maps raw JSON/Map data from MethodChannel into a typed model.
class BoundingBoxModel {
  final List<double> topLeft;
  final List<double> topRight;
  final List<double> bottomRight;
  final List<double> bottomLeft;

  const BoundingBoxModel({
    required this.topLeft,
    required this.topRight,
    required this.bottomRight,
    required this.bottomLeft,
  });

  /// Creates a [BoundingBoxModel] from native platform data.
  ///
  /// Expects a map with 'points' key containing a list of 4 points,
  /// each point being a list of 2 doubles [x, y].
  factory BoundingBoxModel.fromMap(Map<String, dynamic> map) {
    final points = (map['points'] as List<dynamic>)
        .map((point) =>
            (point as List<dynamic>).map((v) => (v as num).toDouble()).toList())
        .toList();

    return BoundingBoxModel(
      topLeft: points[0],
      topRight: points[1],
      bottomRight: points[2],
      bottomLeft: points[3],
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'points': [topLeft, topRight, bottomRight, bottomLeft],
    };
  }
}
