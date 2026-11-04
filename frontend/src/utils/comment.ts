const unsupportedControls=/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]/
const boundaryWhitespace=/^[\s\u200B\uFEFF]+|[\s\u200B\uFEFF]+$/gu

function canonicalize(raw:string):string{
  if(unsupportedControls.test(raw))throw new Error('评论包含无法显示的字符，请调整后重试')
  return raw.replace(/\r\n?/g,'\n').replace(boundaryWhitespace,'')
}
export function commentLength(raw:string):number{return [...canonicalize(raw)].length}
/** Same canonicalization order and code-point limits as Java CommentInputPolicy. */
export function normalizeComment(raw:string):string{
  const value=canonicalize(raw)
  const length=[...value].length
  if(length<1||length>2000)throw new Error('评论须为1至2000个字符')
  return value
}
