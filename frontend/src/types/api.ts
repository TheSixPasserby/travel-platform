/** 后端统一响应包装。仅在请求边界解析，业务代码只接触解包后的数据。 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 参数校验失败时随 40001 返回的字段级错误。 */
export interface FieldError {
  field: string
  message: string
}

/** 40001 响应中 data 的结构。 */
export interface ValidationErrorData {
  fieldErrors?: FieldError[]
}

/** 服务端分页结构，M1 暂未使用页面，但接口类型需与契约一致。 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}
