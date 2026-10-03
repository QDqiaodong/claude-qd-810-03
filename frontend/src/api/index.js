import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

async function unwrap(promise) {
  const { data } = await promise
  if (!data || data.ok === false) {
    const msg = (data && data.message) || '接口返回异常'
    ElMessage.error(msg)
    throw new Error(msg)
  }
  return data.data
}

const qs = (params) => ({ params: params || {} })

export const api = {
  // 矿区与钻孔
  mines: () => unwrap(http.get('/mines')),
  mineStats: () => unwrap(http.get('/mines/stats')),
  boreholes: (mineId) => unwrap(http.get('/boreholes', qs(mineId ? { mineId } : {}))),
  borehole: (id) => unwrap(http.get(`/boreholes/${id}`)),
  createBorehole: (body) => unwrap(http.post('/boreholes', body)),
  updateBorehole: (id, body) => unwrap(http.put(`/boreholes/${id}`, body)),
  deleteBorehole: (id) => unwrap(http.delete(`/boreholes/${id}`)),

  // 钻机与班组
  rigs: () => unwrap(http.get('/rigs')),
  rig: (id) => unwrap(http.get(`/rigs/${id}`)),
  createRig: (body) => unwrap(http.post('/rigs', body)),
  updateRig: (id, body) => unwrap(http.put(`/rigs/${id}`, body)),
  deleteRig: (id) => unwrap(http.delete(`/rigs/${id}`)),
  rigEfficiency: () => unwrap(http.get('/rigs/efficiency')),
  crews: (rigId) => unwrap(http.get('/crews', qs(rigId ? { rigId } : {}))),
  createCrew: (body) => unwrap(http.post('/crews', body)),
  deleteCrew: (id) => unwrap(http.delete(`/crews/${id}`)),

  // 岩芯编录
  rounds: (boreholeId) => unwrap(http.get('/core-rounds', qs(boreholeId ? { boreholeId } : {}))),
  createRound: (body) => unwrap(http.post('/core-rounds', body)),
  updateRound: (id, body) => unwrap(http.put(`/core-rounds/${id}`, body)),
  deleteRound: (id) => unwrap(http.delete(`/core-rounds/${id}`)),
  layers: (params) => unwrap(http.get('/litho-layers', qs(params))),
  layer: (id) => unwrap(http.get(`/litho-layers/${id}`)),
  createLayer: (body) => unwrap(http.post('/litho-layers', body)),
  updateLayer: (id, body) => unwrap(http.put(`/litho-layers/${id}`, body)),
  updateAttrs: (id, attrs) => unwrap(http.put(`/litho-layers/${id}/attrs`, attrs)),
  deleteLayer: (id) => unwrap(http.delete(`/litho-layers/${id}`)),
  lithoStats: () => unwrap(http.get('/core-logs/lithology-stats')),
  lowRecovery: () => unwrap(http.get('/core-logs/low-recovery')),

  // 钻进参数
  series: (boreholeId) => unwrap(http.get('/param-series', qs(boreholeId ? { boreholeId } : {}))),
  analysis: (id) => unwrap(http.get(`/param-series/${id}/analysis`)),

  // 班报
  reports: (params) => unwrap(http.get('/shift-reports', qs(params))),
  report: (id) => unwrap(http.get(`/shift-reports/${id}`)),
  createReport: (body) => unwrap(http.post('/shift-reports', body)),
  updateReport: (id, body) => unwrap(http.put(`/shift-reports/${id}`, body)),
  deleteReport: (id) => unwrap(http.delete(`/shift-reports/${id}`)),

  // 终孔交接台
  handovers: (status) => unwrap(http.get('/handovers', qs(status ? { status } : {}))),
  handoverVersions: (boreholeId) => unwrap(http.get(`/handovers/borehole/${boreholeId}/versions`)),
  handoverDetail: (id) => unwrap(http.get(`/handovers/${id}`)),
  liveIssues: (boreholeId) => unwrap(http.get(`/handovers/borehole/${boreholeId}/live-issues`)),
  initiateHandover: (boreholeId, body) => unwrap(http.post(`/handovers/borehole/${boreholeId}/initiate`, body || {})),
  saveProgress: (id, body) => unwrap(http.post(`/handovers/${id}/save-progress`, body || {})),
  resubmitHandover: (id, body) => unwrap(http.post(`/handovers/${id}/resubmit`, body || {})),
  rejectHandover: (id, body) => unwrap(http.post(`/handovers/${id}/reject`, body)),
  acceptHandover: (id, body) => unwrap(http.post(`/handovers/${id}/accept`, body || {})),
  reopenHandover: (id, body) => unwrap(http.post(`/handovers/${id}/reopen`, body)),

  // 岩芯样品取样与送检
  samples: (params) => unwrap(http.get('/core-samples', qs(params))),
  packableSamples: (boreholeId) =>
    unwrap(http.get('/core-samples/packable', qs(boreholeId ? { boreholeId } : {}))),
  sample: (id) => unwrap(http.get(`/core-samples/${id}`)),
  createSample: (body) => unwrap(http.post('/core-samples', body)),
  updateSample: (id, body) => unwrap(http.put(`/core-samples/${id}`, body)),
  deleteSample: (id, expectedVersion) =>
    unwrap(http.delete(`/core-samples/${id}`, qs(expectedVersion != null ? { expectedVersion } : {}))),

  sampleBatches: (status) => unwrap(http.get('/sample-batches', qs(status ? { status } : {}))),
  sampleBatchDetail: (id) => unwrap(http.get(`/sample-batches/${id}`)),
  packBatch: (body) => unwrap(http.post('/sample-batches/pack', body)),
  receiveBatch: (id, body) => unwrap(http.post(`/sample-batches/${id}/receive`, body)),
  returnBatch: (id, body) => unwrap(http.post(`/sample-batches/${id}/return`, body))
}
