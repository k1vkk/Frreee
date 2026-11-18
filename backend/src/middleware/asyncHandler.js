export function asyncHandler(fn) {
  return function asyncRouteWrapper(req, res, next) {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}
