// Mirrors the backend's JSON records. Nullable fields are typed `| null`.

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
};

export type Profile = {
  id: string;
  email: string;
  displayName: string | null;
  city: string | null;
  postalCode: string | null;
  contactNote: string | null;
  profileComplete: boolean;
};

export type ConditionGrade = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'ACCEPTABLE' | 'POOR';
export type GradeInfo = { code: ConditionGrade; label: string; criteria: string };
export type CopyStatus = 'AVAILABLE' | 'UNLISTED' | 'RESERVED';
export type DetailsSource = 'CATALOG' | 'MANUAL';

export type Copy = {
  id: string;
  isbn: string;
  title: string;
  author: string | null;
  coverUrl: string | null;
  grade: ConditionGrade;
  status: CopyStatus;
  detailsSource: DetailsSource;
  createdAt: string;
  updatedAt: string;
};

export type CopyCard = {
  copyId: string;
  isbn: string;
  title: string;
  author: string | null;
  coverUrl: string | null;
  grade: ConditionGrade;
  detailsSource: DetailsSource;
  owner: { id: string; displayName: string; city: string | null };
};

export type BookLookup = {
  isbn: string;
  title: string;
  author: string | null;
  coverUrl: string | null;
  source: 'LOCAL' | 'CATALOG';
};

export type ProposalStatus =
  | 'PROPOSED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'COUNTERED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'COMPLETED';
export type CancelCause = 'WITHDRAWN' | 'COPY_UNAVAILABLE' | 'NO_SHOW';

export type Party = {
  id: string;
  displayName: string;
  city?: string | null;
  email?: string | null;
  contactNote?: string | null;
};

export type Item = {
  copyId: string | null;
  isbn: string;
  title: string;
  author: string | null;
  coverUrl: string | null;
  detailsSource: DetailsSource;
  gradeAtProposal: ConditionGrade;
};

export type Proposal = {
  id: string;
  status: ProposalStatus;
  cancelCause: CancelCause | null;
  parentId: string | null;
  message: string | null;
  rejectReason: string | null;
  createdAt: string;
  expiresAt: string;
  acceptedAt: string | null;
  confirmBy: string | null;
  completedAt: string | null;
  confirmedByMe: boolean | null;
  confirmedByOther: boolean | null;
  pickupNote: string | null;
  proposer: Party;
  recipient: Party;
  offered: Item[];
  requested: Item[];
};

export type Exchange = {
  proposalId: string;
  completedAt: string;
  with: Party;
  gave: Item[];
  received: Item[];
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ApiErrorBody = {
  status: number;
  code: string;
  message: string;
  fieldErrors: { field: string; message: string }[] | null;
  timestamp: string;
  path: string;
};
