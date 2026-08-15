/**
 * object-fit:contain 渲染区域计算（纯函数）。
 *
 * 输入容器尺寸与图片原始尺寸，输出图片在容器内的实际渲染宽高 (rw/rh) 与
 * 居中留白偏移 (ox/oy)。归一化坐标 (0~1) 映射到像素：
 *   px = ox + normalized * rw
 *   py = oy + normalized * rh
 * 容器/图片尺寸为 0 时按 1 处理，避免除零。
 */
export interface ContainRect {
  rw: number
  rh: number
  ox: number
  oy: number
}

export function computeContainRect(
  containerW: number,
  containerH: number,
  imageW: number,
  imageH: number,
): ContainRect {
  const cw = containerW || 1
  const ch = containerH || 1
  const iw = imageW || 1
  const ih = imageH || 1
  const imageAspect = iw / ih
  const containerAspect = cw / ch

  let rw: number
  let rh: number
  let ox: number
  let oy: number
  if (imageAspect > containerAspect) {
    rw = cw
    rh = cw / imageAspect
    ox = 0
    oy = (ch - rh) / 2
  } else {
    rh = ch
    rw = ch * imageAspect
    ox = (cw - rw) / 2
    oy = 0
  }
  return { rw, rh, ox, oy }
}
