import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';

/// Base use case interface.
///
/// Every use case has a single `call` method that takes [Params]
/// and returns `Either<Failure, Output>`.
abstract class UseCase<Output, Params> {
  Future<Either<Failure, Output>> call(Params params);
}

/// Use when the use case requires no parameters.
class NoParams {
  const NoParams();
}
