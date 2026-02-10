import 'package:dartz/dartz.dart';

/// Convenience extensions on [Either] for cleaner code.
extension EitherX<L, R> on Either<L, R> {
  /// Get the right (success) value or null.
  R? get rightOrNull => fold((_) => null, (r) => r);

  /// Get the left (failure) value or null.
  L? get leftOrNull => fold((l) => l, (_) => null);

  /// Check if this is a right (success) value.
  bool get isRight => fold((_) => false, (_) => true);

  /// Check if this is a left (failure) value.
  bool get isLeft => fold((_) => true, (_) => false);
}
