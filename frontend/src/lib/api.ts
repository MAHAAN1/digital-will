import {
  ApiErrorResponse,
  Asset,
  AssetAllocation,
  AuditLogDto,
  AuditVerificationResult,
  AuthResponse,
  Beneficiary,
  DisclosureResponse,
  DocumentResponse,
  User,
  VerificationConfirmationResult,
  WillContactDetailed,
  WillResponse,
  WillReview,
} from './types';

export class ApiException extends Error {
  public status: number;
  public code: string;
  public path: string;
  public details?: Record<string, string>;

  constructor(errorResponse: ApiErrorResponse) {
    super(errorResponse.message);
    this.name = 'ApiException';
    this.status = errorResponse.status;
    this.code = errorResponse.code;
    this.path = errorResponse.path;
    this.details = errorResponse.details;
  }
}

// The custom header distinguishes browser sessions from native bearer-token clients.
function request(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set('X-Requested-With', 'DigitalWill');
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json');
  return fetch(path, { ...init, headers, credentials: 'same-origin', cache: 'no-store' });
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let errorData: ApiErrorResponse;
    try {
      errorData = await res.json();
    } catch {
      errorData = {
        timestamp: new Date().toISOString(),
        status: res.status,
        code: res.status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'HTTP_ERROR',
        message: res.statusText || 'An unexpected error occurred',
        path: res.url,
      };
    }
    throw new ApiException(errorData);
  }
  return res.json() as Promise<T>;
}

export const api = {
  // --- Authentication ---
  async register(email: string, password: string, fullName: string): Promise<AuthResponse> {
    const res = await request(`/api/auth/register`, {
      method: 'POST',
      body: JSON.stringify({ email, password, fullName }),
    });
    return handleResponse<AuthResponse>(res);
  },

  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await request(`/api/auth/login`, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    return handleResponse<AuthResponse>(res);
  },

  async getSession(): Promise<User | null> {
    const res = await request('/api/auth/session');
    return res.status === 204 ? null : handleResponse<User>(res);
  },

  async getMe(): Promise<User> {
    const res = await request(`/api/auth/me`);
    return handleResponse<User>(res);
  },

  async logout(): Promise<void> {
    const res = await request('/api/auth/logout', { method: 'POST' });
    if (res.status !== 401) await handleResponse<{ message: string }>(res);
  },

  // --- Wills / Estate ---
  async getMyWill(): Promise<WillResponse | null> {
    try {
      const res = await request(`/api/wills/my`, {
        });
      if (res.status === 204 || res.status === 404) return null;
      return await handleResponse<WillResponse>(res);
    } catch (err) {
      if (err instanceof ApiException && err.status === 404) {
        return null;
      }
      throw err;
    }
  },

  async getWill(willId: string): Promise<WillResponse> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}`);
    return handleResponse<WillResponse>(res);
  },

  async createWill(title: string): Promise<WillResponse> {
    const res = await request(`/api/wills`, {
      method: 'POST',
      body: JSON.stringify({ title }),
    });
    return handleResponse<WillResponse>(res);
  },

  async checkIn(willId: string): Promise<WillResponse> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/check-in`, {
      method: 'POST',
    });
    return handleResponse<WillResponse>(res);
  },

  async cancelWill(willId: string, reason?: string): Promise<WillResponse> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
    return handleResponse<WillResponse>(res);
  },

  async scheduleRelease(willId: string): Promise<WillResponse> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/schedule-release`, {
      method: 'POST',
    });
    return handleResponse<WillResponse>(res);
  },

  async executeRelease(willId: string): Promise<unknown> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/execute-release`, {
      method: 'POST',
    });
    return handleResponse<unknown>(res);
  },

  // --- Assets ---
  async listAssets(willId: string): Promise<Asset[]> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/assets`);
    return handleResponse<Asset[]>(res);
  },

  async addAsset(willId: string, data: Partial<Asset>): Promise<Asset> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/assets`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
    return handleResponse<Asset>(res);
  },

  async deleteAsset(willId: string, assetId: string): Promise<void> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/assets/${encodeURIComponent(assetId)}`, {
      method: 'DELETE',
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Beneficiaries ---
  async listBeneficiaries(willId: string): Promise<Beneficiary[]> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/beneficiaries`);
    return handleResponse<Beneficiary[]>(res);
  },

  async addBeneficiary(willId: string, data: Partial<Beneficiary>): Promise<Beneficiary> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/beneficiaries`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
    return handleResponse<Beneficiary>(res);
  },

  async deleteBeneficiary(willId: string, beneficiaryId: string): Promise<void> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/beneficiaries/${encodeURIComponent(beneficiaryId)}`, {
      method: 'DELETE',
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Allocations ---
  async listAllocations(willId: string): Promise<AssetAllocation[]> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/allocations`);
    return handleResponse<AssetAllocation[]>(res);
  },

  async allocateAsset(
    willId: string,
    assetId: string,
    beneficiaryId: string,
    sharePercentage: number,
    instructions?: string
  ): Promise<AssetAllocation> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/allocations`, {
      method: 'POST',
      body: JSON.stringify({ assetId, beneficiaryId, sharePercentage, instructions }),
    });
    return handleResponse<AssetAllocation>(res);
  },

  async deleteAllocation(willId: string, allocationId: string): Promise<void> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/allocations/${encodeURIComponent(allocationId)}`, {
      method: 'DELETE',
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Trusted Contacts ---
  async listContacts(willId: string): Promise<WillContactDetailed[]> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/contacts`);
    return handleResponse<WillContactDetailed[]>(res);
  },

  async addContact(willId: string, name: string, email: string): Promise<WillContactDetailed> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/contacts`, {
      method: 'POST',
      body: JSON.stringify({ name, email }),
    });
    return handleResponse<WillContactDetailed>(res);
  },

  async deactivateContact(willId: string, contactId: string): Promise<void> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/contacts/${encodeURIComponent(contactId)}`, {
      method: 'DELETE',
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Review & Audit ---
  async getWillReview(willId: string): Promise<WillReview> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/review`);
    return handleResponse<WillReview>(res);
  },

  async getWillAuditLogs(willId: string): Promise<AuditLogDto[]> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/audit`);
    return handleResponse<AuditLogDto[]>(res);
  },

  async verifyAuditChain(willId: string): Promise<AuditVerificationResult> {
    const res = await request(`/api/wills/${encodeURIComponent(willId)}/audit/verify`);
    return handleResponse<AuditVerificationResult>(res);
  },

  // --- Documents ---
  async uploadDocument(willId: string, file: File): Promise<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('willId', willId);

    const res = await request(`/api/documents/upload`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse<DocumentResponse>(res);
  },

  async listDocuments(willId: string): Promise<DocumentResponse[]> {
    const res = await request(`/api/documents/will/${encodeURIComponent(willId)}`);
    return handleResponse<DocumentResponse[]>(res);
  },

  async downloadDocument(documentId: string, willId: string, fileName: string): Promise<void> {
    const res = await request(`/api/documents/${encodeURIComponent(documentId)}/download?willId=${encodeURIComponent(willId)}`);

    if (!res.ok) {
      return handleResponse(res);
    }

    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },

  // --- Public Contact Verification ---
  async confirmVerification(token: string): Promise<VerificationConfirmationResult> {
    const res = await request(`/api/verification/confirm`, {
      method: 'POST',
      body: JSON.stringify({ token }),
    });
    return handleResponse<VerificationConfirmationResult>(res);
  },

  // --- Public Controlled Disclosure ---
  async getDisclosure(token: string): Promise<DisclosureResponse> {
    const res = await request(`/api/disclosure/${encodeURIComponent(token)}`);
    return handleResponse<DisclosureResponse>(res);
  },

  async downloadDisclosedDocument(token: string, documentId: string, fileName: string): Promise<void> {
    const res = await request(`/api/disclosure/${encodeURIComponent(token)}/document/${encodeURIComponent(documentId)}`);
    if (!res.ok) {
      return handleResponse(res);
    }
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },
};
