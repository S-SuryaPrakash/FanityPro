import * as XLSX from 'xlsx'

// Sheet names and headers below must match ExcelReportService (Spring Boot) exactly —
// see SUMMARY_SHEET / REVIEW_QUEUE_SHEET and the header arrays in createSummary()/createReviewQueue().
const SUMMARY_SHEET = 'Summary'
const REVIEW_QUEUE_SHEET = 'Review Queue'
const TOTAL_ROW_LABEL = 'All Classified Sequences'

export interface CategoryCount {
  category: string
  total: number
  reviewRequired: number
}

export interface ReviewQueueItem {
  priority: number
  text: string
  primaryCategory: string
  severity: string
  confidence: number
  reviewReason: string
}

export interface ClassificationResults {
  totalMessages: number
  reviewRequiredCount: number
  categories: CategoryCount[]
  reviewQueue: ReviewQueueItem[]
}

/**
 * Reads the "Summary" and "Review Queue" sheets that ExcelReportService adds to every
 * classified workbook. These sheets have a fixed, code-defined shape (unlike the annotated
 * source sheet, whose columns shift with the uploaded file), so parsing them client-side is
 * safe without re-deriving the classification pipeline in the browser.
 */
export function parseClassificationResults(buffer: ArrayBuffer): ClassificationResults {
  const workbook = XLSX.read(buffer, { type: 'array' })

  const summarySheet = workbook.Sheets[SUMMARY_SHEET]
  const reviewSheet = workbook.Sheets[REVIEW_QUEUE_SHEET]
  if (!summarySheet || !reviewSheet) {
    throw new Error('The classified workbook is missing expected report sheets.')
  }

  const summaryRows = XLSX.utils.sheet_to_json<Record<string, unknown>>(summarySheet, { defval: null })
  const categories: CategoryCount[] = []
  let totalMessages = 0
  let reviewRequiredCount = 0

  for (const row of summaryRows) {
    const category = row['Primary Category']
    if (typeof category !== 'string' || category.length === 0) continue
    const total = Number(row['Total'] ?? 0)
    const reviewRequired = Number(row['Review Required'] ?? 0)
    if (category === TOTAL_ROW_LABEL) {
      totalMessages = total
      reviewRequiredCount = reviewRequired
      continue
    }
    if (total > 0) categories.push({ category, total, reviewRequired })
  }

  const reviewRows = XLSX.utils.sheet_to_json<Record<string, unknown>>(reviewSheet, { defval: null })
  const reviewQueue: ReviewQueueItem[] = reviewRows
    .filter(row => row['Priority'] != null)
    .map(row => ({
      priority: Number(row['Priority']),
      text: String(row['Text'] ?? ''),
      primaryCategory: String(row['Primary Category'] ?? ''),
      severity: String(row['Severity'] ?? ''),
      confidence: Number(row['Confidence'] ?? 0),
      reviewReason: String(row['Review Reason'] ?? ''),
    }))

  return { totalMessages, reviewRequiredCount, categories, reviewQueue }
}

const DETECTED_RISK_CATEGORIES = new Set([
  'Threat',
  'Hate Or Identity Attack',
  'Harassment Or Insult',
  'Obscene Or Profane',
  'General Toxicity',
])

/** Mirrors RiskCategory.isDetectedRisk() (Java) for badge styling — no severity data is duplicated here. */
export function categoryTone(category: string): 'danger' | 'warning' | 'neutral' {
  if (DETECTED_RISK_CATEGORIES.has(category)) return 'danger'
  if (category === 'Manual Review') return 'warning'
  return 'neutral'
}
