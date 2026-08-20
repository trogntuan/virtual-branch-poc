export interface ApiError {
  code: string;
  message: string;
  timestamp: string;
}

export interface SessionResponse {
  sessionId: string;
  roomName: string;
  status: string;
  createdAt: string;
  endedAt: string | null;
  customerIdentity: string | null;
  customerName: string | null;
  agentIdentity: string | null;
  agentName: string | null;
  acceptedAt: string | null;
}

export interface TokenResponse {
  serverUrl: string;
  roomName: string;
  participantToken: string;
}

export interface RecordingResponse {
  recordingId: string;
  sessionId: string;
  egressId: string | null;
  status: string;
  objectKey: string | null;
  playbackUrl: string | null;
  errorMessage: string | null;
}

export interface DocumentResponse {
  documentId: string;
  fileName: string;
  contentType: string;
  size: number;
  readUrl: string;
}

export interface DocumentUrlResponse {
  documentId: string;
  readUrl: string;
  expiresInSeconds: number;
}

export interface MobileDisplayResponse {
  viewportWidth: number | null;
  viewportHeight: number | null;
  devicePixelRatio: number | null;
  orientation: string | null;
}

export interface DocCollabResponse {
  collabId: string;
  sessionId: string;
  documentId: string;
  status: string;
  consentDecision: string | null;
}

export type ParticipantRole = 'AGENT' | 'CUSTOMER';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
      ...init,
    });
  } catch {
    throw new Error('Không kết nối được backend. Kiểm tra Spring Boot đang chạy.');
  }

  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try {
      const error = (await response.json()) as ApiError;
      message = error.code ? `${error.code}: ${error.message}` : (error.message || message);
    } catch {
      // ignore parse errors
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function createSession(): Promise<SessionResponse> {
  return request<SessionResponse>('/api/v1/sessions', { method: 'POST' });
}

export async function requestCall(
  identity: string,
  name: string,
  mobileDisplay: {
    viewportWidth: number;
    viewportHeight: number;
    devicePixelRatio: number;
    orientation: string;
  },
): Promise<SessionResponse> {
  return request<SessionResponse>('/api/v1/calls', {
    method: 'POST',
    body: JSON.stringify({
      identity,
      name,
      ...mobileDisplay,
    }),
  });
}

export async function listWaitingCalls(): Promise<SessionResponse[]> {
  return request<SessionResponse[]>('/api/v1/calls/waiting');
}

export async function acceptCall(
  sessionId: string,
  identity: string,
  name: string,
): Promise<SessionResponse> {
  return request<SessionResponse>(`/api/v1/calls/${sessionId}/accept`, {
    method: 'POST',
    body: JSON.stringify({ identity, name }),
  });
}

export async function getSession(sessionId: string): Promise<SessionResponse> {
  return request<SessionResponse>(`/api/v1/sessions/${sessionId}`);
}

export async function endSession(sessionId: string): Promise<SessionResponse> {
  return request<SessionResponse>(`/api/v1/sessions/${sessionId}/end`, { method: 'POST' });
}

export async function issueToken(
  sessionId: string,
  identity: string,
  name: string,
  role: ParticipantRole,
): Promise<TokenResponse> {
  return request<TokenResponse>(`/api/v1/sessions/${sessionId}/token`, {
    method: 'POST',
    body: JSON.stringify({ identity, name, role }),
  });
}

export async function startRecording(sessionId: string): Promise<RecordingResponse> {
  return request<RecordingResponse>(`/api/v1/sessions/${sessionId}/recordings`, { method: 'POST' });
}

export async function getRecording(recordingId: string): Promise<RecordingResponse> {
  return request<RecordingResponse>(`/api/v1/recordings/${recordingId}`);
}

export async function stopRecording(recordingId: string): Promise<RecordingResponse> {
  return request<RecordingResponse>(`/api/v1/recordings/${recordingId}/stop`, { method: 'POST' });
}

export async function uploadDocument(sessionId: string, file: File): Promise<DocumentResponse> {
  const formData = new FormData();
  formData.append('file', file);

  let response: Response;
  try {
    response = await fetch(`/api/v1/sessions/${sessionId}/documents`, {
      method: 'POST',
      body: formData,
    });
  } catch {
    throw new Error('Không kết nối được backend. Kiểm tra Spring Boot đang chạy.');
  }

  if (!response.ok) {
    let message = `Tải lên thất bại (${response.status})`;
    try {
      const error = (await response.json()) as ApiError;
      message = error.code ? `${error.code}: ${error.message}` : (error.message || message);
    } catch {
      // ignore parse errors
    }
    throw new Error(message);
  }

  return (await response.json()) as DocumentResponse;
}

export async function getDocumentUrl(documentId: string): Promise<DocumentUrlResponse> {
  return request<DocumentUrlResponse>(`/api/v1/documents/${documentId}/url`);
}

export async function updateMobileDisplay(
  sessionId: string,
  profile: {
    viewportWidth: number;
    viewportHeight: number;
    devicePixelRatio: number;
    orientation: string;
  },
): Promise<MobileDisplayResponse> {
  return request<MobileDisplayResponse>(`/api/v1/sessions/${sessionId}/mobile-display`, {
    method: 'PUT',
    body: JSON.stringify(profile),
  });
}

export async function getMobileDisplay(sessionId: string): Promise<MobileDisplayResponse> {
  return request<MobileDisplayResponse>(`/api/v1/sessions/${sessionId}/mobile-display`);
}

export async function startDocCollab(
  sessionId: string,
  documentId: string,
): Promise<DocCollabResponse> {
  return request<DocCollabResponse>(`/api/v1/sessions/${sessionId}/doc-collabs`, {
    method: 'POST',
    body: JSON.stringify({ documentId }),
  });
}

export async function getDocCollab(collabId: string): Promise<DocCollabResponse> {
  return request<DocCollabResponse>(`/api/v1/doc-collabs/${collabId}`);
}

export async function submitCollabConsent(
  collabId: string,
  decision: 'ACCEPT' | 'REJECT',
): Promise<DocCollabResponse> {
  return request<DocCollabResponse>(`/api/v1/doc-collabs/${collabId}/consent`, {
    method: 'POST',
    body: JSON.stringify({ decision }),
  });
}

export async function getCollabDocumentUrl(collabId: string): Promise<DocumentUrlResponse> {
  return request<DocumentUrlResponse>(`/api/v1/doc-collabs/${collabId}/document-url`);
}

export async function endDocCollab(collabId: string): Promise<DocCollabResponse> {
  return request<DocCollabResponse>(`/api/v1/doc-collabs/${collabId}/end`, { method: 'POST' });
}
