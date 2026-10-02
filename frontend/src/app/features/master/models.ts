/** Shapes of the master-data APIs: materials, suppliers, customers, products and the spring-attribute catalogue. */
export interface PartnerRef {
  id: number;
  code: string;
  name: string;
}

export type MaterialType =
  'SPRING_STEEL' | 'STAINLESS' | 'HIGH_CARBON' | 'ALLOY' | 'PHOSPHOR_BRONZE' | 'MUSIC_WIRE' | 'CONSUMABLE';

export const MATERIAL_TYPES: { value: MaterialType; label: string }[] = [
  { value: 'SPRING_STEEL', label: 'Spring steel' },
  { value: 'STAINLESS', label: 'Stainless' },
  { value: 'HIGH_CARBON', label: 'High carbon' },
  { value: 'ALLOY', label: 'Alloy' },
  { value: 'PHOSPHOR_BRONZE', label: 'Phosphor bronze' },
  { value: 'MUSIC_WIRE', label: 'Music wire' },
  { value: 'CONSUMABLE', label: 'Consumable' },
];

export interface MaterialSummary {
  id: number;
  code: string;
  name: string;
  materialType: MaterialType;
  grade?: string;
  diameterMm?: number;
  uom: string;
  preferredSupplier?: PartnerRef;
  minStock?: number;
  reorderLevel?: number;
  maxStock?: number;
  standardCost?: number;
  active: boolean;
}

export interface MaterialResponse extends MaterialSummary {
  shelfLifeDays?: number;
  description?: string;
  version: number;
}

export interface MaterialRequest {
  code?: string;
  name: string;
  materialType: MaterialType;
  grade?: string;
  diameterMm?: number | null;
  uom: string;
  preferredSupplierId?: number | null;
  minStock?: number | null;
  reorderLevel?: number | null;
  maxStock?: number | null;
  standardCost?: number | null;
  shelfLifeDays?: number | null;
  description?: string;
  version?: number;
}

export interface Uom {
  code: string;
  name: string;
  kind: string;
}

export interface PartnerSummary {
  id: number;
  code: string;
  name: string;
  contactPerson?: string;
  phone?: string;
  email?: string;
  gstNumber?: string;
  active: boolean;
}

export interface PartnerResponse extends PartnerSummary {
  address?: string;
  paymentTerms?: string;
  leadTimeDays?: number;
  version: number;
}

export interface PartnerRequest {
  code?: string;
  name: string;
  contactPerson?: string;
  phone?: string;
  email?: string;
  address?: string;
  gstNumber?: string;
  paymentTerms?: string;
  leadTimeDays?: number | null;
  version?: number;
}

export type SpringType =
  'COMPRESSION' | 'EXTENSION' | 'TORSION' | 'CONICAL' | 'DISC_BELLEVILLE' | 'WIRE_FORM' | 'CUSTOM';
export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'OBSOLETE';
export type ProductAction = 'EDIT' | 'ACTIVATE' | 'OBSOLETE';

export interface SpringTypeInfo {
  type: SpringType;
  label: string;
  attributeCount: number;
  freeForm: boolean;
}

export interface AttributeDefinition {
  code: string;
  label: string;
  dataType: 'NUMBER' | 'TEXT' | 'ENUM' | 'BOOLEAN';
  unit?: string;
  required: boolean;
  minValue?: number;
  maxValue?: number;
  enumValues: string[];
  displayOrder: number;
  /** CORE: a top-level product field. SPECIFICATIONS: an entry of the specifications map. */
  storage: 'CORE' | 'SPECIFICATIONS';
}

export interface ProductSummary {
  id: number;
  code: string;
  name: string;
  springType: SpringType;
  primaryMaterial?: PartnerRef;
  wireDiameter?: number;
  outerDiameter?: number;
  freeLength?: number;
  drawingNumber?: string;
  drawingRevision?: string;
  customer?: PartnerRef;
  status: ProductStatus;
  active: boolean;
  /** What the caller may do now: the lifecycle state intersected with their permissions. */
  allowedActions: ProductAction[];
}

/** The product as the API returns it; any column may also appear as a core attribute of the spring type. */
export interface ProductResponse extends ProductSummary {
  [core: string]: unknown;
  innerDiameter?: number;
  surfaceTreatment?: string;
  heatTreatment?: string;
  tolerance?: string;
  unitWeightKg?: number;
  uom?: string;
  reorderLevel?: number;
  standardCost?: number;
  specifications: Record<string, string | number | boolean>;
  version: number;
}

/** Product columns that a spring type's catalogue may describe (storage CORE). */
export const CORE_ATTRIBUTE_CODES = [
  'wireDiameter',
  'outerDiameter',
  'innerDiameter',
  'freeLength',
  'numberOfCoils',
  'activeCoils',
  'springRate',
  'maxLoad',
  'minLoad',
  'workingLength',
  'solidHeight',
  'endType',
] as const;
