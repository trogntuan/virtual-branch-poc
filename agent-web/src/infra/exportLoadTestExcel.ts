import * as XLSX from 'xlsx';
import type { RecordingResponse } from '../api/virtualBranchApi';

export type WorkerExportStatus =
  | 'pending'
  | 'running'
  | 'recording'
  | 'stopping'
  | 'done'
  | 'failed';

export interface WorkerExportRow {
  index: number;
  sessionId: string;
  recordingId?: string;
  status: WorkerExportStatus;
  error?: string;
  startedAt?: number;
  stoppedAt?: number;
  recording?: RecordingResponse;
}

export interface MetricSampleRow {
  t: string;
  egressCpu: number | null;
  sfuCpu: number | null;
  minioNet?: string;
  sfuRooms?: number | null;
  sfuParticipants?: number | null;
  sfuMem?: string | null;
  egressMem?: string | null;
}

export interface LoadTestExportInput {
  startedAt: string;
  finishedAt: string;
  concurrency: number;
  durationSec: number;
  peakEgressCpu: number;
  peakSfuCpu: number;
  okCount: number;
  failedCount: number;
  workers: WorkerExportRow[];
  samples: MetricSampleRow[];
  finalContainers?: Array<{
    name: string;
    cpuPercent?: number | null;
    memUsage?: string;
    memPercent?: number | null;
    netIO?: string;
    blockIO?: string;
    error?: string;
  }>;
  finalSfu?: {
    cpuPercent?: number | null;
    memUsage?: string | null;
    rooms?: number | null;
    participants?: number | null;
    packetsIn?: number | null;
    packetsOut?: number | null;
    packetsDropped?: number | null;
  };
}

function stampFileName(iso: string): string {
  return iso.replace(/[:.]/g, '-').slice(0, 19);
}

function durationSecOf(w: WorkerExportRow): number | '' {
  if (!w.startedAt || !w.stoppedAt) {
    return '';
  }
  return Math.round((w.stoppedAt - w.startedAt) / 1000);
}

export function downloadLoadTestExcel(input: LoadTestExportInput): string {
  const workbook = XLSX.utils.book_new();

  const summaryRows = [
    { Field: 'Started at', Value: input.startedAt },
    { Field: 'Finished at', Value: input.finishedAt },
    { Field: 'Concurrency', Value: input.concurrency },
    { Field: 'Record duration (s)', Value: input.durationSec },
    { Field: 'Workers OK', Value: input.okCount },
    { Field: 'Workers failed', Value: input.failedCount },
    { Field: 'Peak egress CPU %', Value: round1(input.peakEgressCpu) },
    { Field: 'Peak SFU CPU %', Value: round1(input.peakSfuCpu) },
    { Field: 'SFU CPU final %', Value: round1(input.finalSfu?.cpuPercent) },
    { Field: 'SFU MEM final', Value: input.finalSfu?.memUsage ?? '' },
    { Field: 'SFU rooms final', Value: input.finalSfu?.rooms ?? '' },
    { Field: 'SFU participants final', Value: input.finalSfu?.participants ?? '' },
    { Field: 'SFU packets in', Value: input.finalSfu?.packetsIn ?? '' },
    { Field: 'SFU packets out', Value: input.finalSfu?.packetsOut ?? '' },
    { Field: 'SFU packets dropped', Value: input.finalSfu?.packetsDropped ?? '' },
    { Field: 'Metric samples', Value: input.samples.length },
  ];
  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(summaryRows),
    'Summary',
  );

  const workerRows = input.workers.map((w) => ({
    Call: w.index + 1,
    Status: w.status,
    SessionId: w.sessionId,
    RecordingId: w.recordingId ?? '',
    RecordingStatus: w.recording?.status ?? '',
    ObjectKey:
      w.recording?.tracks?.map((t) => t.objectKey).filter(Boolean).join(' | ')
      || w.recording?.objectKey
      || '',
    PlaybackUrl:
      w.recording?.tracks?.map((t) => t.playbackUrl).filter(Boolean).join(' | ')
      || w.recording?.playbackUrl
      || '',
    DurationSec: durationSecOf(w),
    StartedAt: w.startedAt ? new Date(w.startedAt).toISOString() : '',
    StoppedAt: w.stoppedAt ? new Date(w.stoppedAt).toISOString() : '',
    Error: w.error ?? '',
  }));
  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(workerRows),
    'Workers',
  );

  const sampleRows = input.samples.map((s, i) => ({
    Sample: i + 1,
    Timestamp: s.t,
    SfuCpuPercent: s.sfuCpu ?? '',
    EgressCpuPercent: s.egressCpu ?? '',
    SfuRooms: s.sfuRooms ?? '',
    SfuParticipants: s.sfuParticipants ?? '',
    SfuMem: s.sfuMem ?? '',
    EgressMem: s.egressMem ?? '',
    MinioNetIO: s.minioNet ?? '',
  }));
  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(sampleRows.length > 0 ? sampleRows : [{ Sample: '', Note: 'No samples' }]),
    'MetricSamples',
  );

  if (input.finalContainers && input.finalContainers.length > 0) {
    const containerRows = input.finalContainers.map((c) => ({
      Container: c.name,
      CpuPercent: c.cpuPercent ?? '',
      MemUsage: c.memUsage ?? '',
      MemPercent: c.memPercent ?? '',
      NetIO: c.netIO ?? '',
      BlockIO: c.blockIO ?? '',
      Error: c.error ?? '',
    }));
    XLSX.utils.book_append_sheet(
      workbook,
      XLSX.utils.json_to_sheet(containerRows),
      'ContainersFinal',
    );
  }

  const fileName = `vb-infra-loadtest-${stampFileName(input.finishedAt)}.xlsx`;
  XLSX.writeFile(workbook, fileName);
  return fileName;
}

function round1(value: number | null | undefined): number | '' {
  if (value == null || Number.isNaN(value)) {
    return '';
  }
  return Math.round(value * 10) / 10;
}
