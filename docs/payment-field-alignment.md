# 請款簽核系統欄位名稱盤點與對齊報告

> **Superseded（產品契約）：** 欄位中文用語、`APPROVED=核准結案`、出納＝`CASHIER`、付款證明 B1 以 [`excel-sync-roadmap.md`](excel-sync-roadmap.md) 與 `請款流程_已完成開發規則.xlsx` 為準。本文件保留 2026-08-03 盤點歷史；其中「已核准」「PAYMENT_OPERATOR」等建議已覆寫。

盤點日期：2026-08-03

本文件只做欄位盤點、名稱對照與規格差異記錄。本階段沒有修改 Java、Vue、Migration、Database 或 API Contract，也沒有執行案件 14、20 的付款寫入 E2E。

## 1. 盤點依據與優先順序

本次實際讀取：

- 原始 Excel：`請款流程_已完成開發規則.xlsx`
  - `欄位`：A1:W29
  - `簽核流程規則`：A1:F51
  - `結果檔案`：A1:D2
- Backend：V1～V4 Migration、Entity、Enum、Request/Response DTO、Controller、Service、Security Entity/DTO/Enum。
- Frontend：payment/workflow/auth/master TypeScript types、API modules、Store、Payment views、Manager/Cashier views、Item Editor、AppLayout、表單與表格 labels。

判斷優先順序為：已確認核心業務規則 → V1～V4 Migration → 實際 Backend → 實際 Frontend → Excel 既有內容。Excel 與目前系統不一致時，本文件保留差異，不直接改名。

## 2. Excel 三個分頁摘要

### 2.1 欄位

原始欄位列包含請款單號、客戶代號／名稱、支出／代墊、帳冊表格所屬公司、部門、事由、費用名稱、起點、終點、費用性質、函證性質、人數、天數、數量、印章大小、單價、來回*2、請款金額、申請人、複核人、出納、附件。

內容另外描述餐費、交通費、函證、規費、影印、打字、印章、登報、帳冊、表格、土地謄本等費用，以及「固定金額由後台維護」、「送出時保存主管快照」、「簽核狀態與付款狀態分開」等備註。

主要問題：`帳冊表格所屬公司` 過度限縮 Company 用途；`費用名稱` 與實際 `ExpenseType` 不完全相同；`費用性質`、`函證性質` 沒有明確對應；`來回*2` 是舊的業務描述，不是固定欄位；`複核人`、`出納` 沒有反映快照與兩種權限的差異。

### 2.2 簽核流程規則

此分頁記錄 Vue 3、Spring Boot、JasperReports／Excel 輸出，以及建立草稿 → 部門主管複核 → 出納確認 → 付款登記 → 結案／查詢的流程。

它已正確表達「出納確認代表核准、不代表已付款」及「簽核狀態與付款狀態分開」，但狀態中文名稱仍使用 `APPROVED = 核准結案`，容易與已付款混淆；付款登記與出納確認也都寫成「出納角色」。

### 2.3 結果檔案

只有：客戶代號、客戶名稱、費用名稱、總金額。這是報表／匯出欄位集合，不是 PaymentRequest API 的完整資料模型。`總金額` 的彙總粒度在 Excel 沒有明確說明，因此維持 `AMBIGUOUS`，不實作報表邏輯。

## 3. 已確認的核心標準

### 3.1 狀態與流程

| 類型 | 代碼 | 建議中文名稱 |
| --- | --- | --- |
| 簽核狀態 | `DRAFT` | 草稿 |
| 簽核狀態 | `PENDING_MANAGER` | 待主管複核 |
| 簽核狀態 | `PENDING_CASHIER` | 待出納確認 |
| 簽核狀態 | `APPROVED` | 已核准 |
| 簽核狀態 | `REJECTED_CLOSED` | 退回結案 |
| 付款狀態 | `UNPAID` | 未付款 |
| 付款狀態 | `PAID` | 已付款 |

流程為 `DRAFT → PENDING_MANAGER → PENDING_CASHIER → APPROVED/UNPAID → APPROVED/PAID`。主管或出納退回為 `REJECTED_CLOSED/UNPAID`，退回後不可重新送審。

### 3.2 權限名稱

| 權限 | 用途 | 不應混用的名稱 |
| --- | --- | --- |
| `CASHIER` | `cashier-approve`、`cashier-reject` | 不等於付款登記權限 |
| `PAYMENT_OPERATOR` | `record-payment` | 不應只寫成「出納」 |

同一使用者可以同時具有兩個權限，但系統判斷分開。主管依 `supervisorSnapshot` 判斷，不使用 `MANAGER` Role；申請人也沒有 `APPLICANT` Role。

## 4. 唯一欄位對照表

狀態值只使用：`KEEP`、`UI_LABEL_RENAME`、`CODE_RENAME_PROPOSED`、`API_BREAKING_CHANGE`、`DB_MIGRATION_REQUIRED`、`OBSOLETE`、`AMBIGUOUS`、`MISSING`、`EXCEL_ONLY`、`REPORT_ONLY`。

### 4.1 組織主檔與 Security

| 業務中文名稱 | 建議中文畫面名稱 | Backend Java Property | API JSON Field | DB Table | DB Column | Frontend TypeScript Property | 所層級 | 是否必填 | 狀態 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 部門 ID | 部門 ID | `Department.id` | `id` | `departments` | `id` | `id` | 組織主檔 | 是 | KEEP | BIGINT / Long |
| 部門代號 | 部門代號 | `Department.code` | `code` | `departments` | `code` | `code` | 組織主檔 | 是 | KEEP | 唯一，最多 50 |
| 部門名稱 | 部門名稱 | `Department.name` | `name` | `departments` | `name` | `name` | 組織主檔 | 是 | KEEP | 最多 100 |
| 部門啟用 | 部門啟用 | `Department.active` | `active` | `departments` | `active` | `active` | 組織主檔 | 是 | MISSING | 目前選項 API 不回傳 active |
| 使用者 ID | 使用者 ID | `AppUser.id` | `id` | `app_users` | `id` | `userId`／`id` | Security | 是 | KEEP | 申請人、主管、核准人、付款人共用 AppUser |
| 帳號 | 登入帳號 | `AppUser.username` | `username` | `app_users` | `username` | `username` | Security | 是 | KEEP | Login 與 UserSummary 使用 |
| 使用者名稱 | 顯示名稱 | `AppUser.displayName` | `displayName` | `app_users` | `display_name` | `displayName` | 組織主檔／Security | 是 | UI_LABEL_RENAME | Excel 的「員工／複核人／出納」應改為具體角色名稱 |
| Email | Email | `AppUser.email` | 未出現在目前 Payment DTO | `app_users` | `email` | 未使用 | 組織主檔 | 否 | MISSING | Entity 有欄位，現有 API 未輸出 |
| 使用者部門 | 使用者所屬部門 | `AppUser.department` | Detail 透過 `department` | `app_users` | `department_id` | `department` | 組織主檔 | 否 | KEEP | Join 欄位可為 NULL |
| 使用者啟用 | 使用者啟用 | `AppUser.active` | 未輸出 | `app_users` | `active` | 未使用 | 組織主檔／Security | 是 | MISSING | Service 會重新驗證 active |
| 主管對應 | 部門主管設定 | `DepartmentSupervisor.department`／`supervisor` | 未建立主管設定 DTO | `department_supervisors` | `department_id`／`supervisor_id` | 未使用 | 組織主檔 | 是 | MISSING | 目前由 Submit Service 使用 |
| 生效日 | 主管生效日 | `DepartmentSupervisor.effectiveFrom` | 未輸出 | `department_supervisors` | `effective_from` | 未使用 | 組織主檔 | 是 | MISSING | LocalDate |
| 失效日 | 主管失效日 | `DepartmentSupervisor.effectiveTo` | 未輸出 | `department_supervisors` | `effective_to` | 未使用 | 組織主檔 | 否 | MISSING | LocalDate |
| 主管設定啟用 | 主管設定啟用 | `DepartmentSupervisor.active` | 未輸出 | `department_supervisors` | `active` | 未使用 | 組織主檔 | 是 | MISSING | 送出時依有效設定查找 |
| 密碼雜湊 | 密碼雜湊 | `AppUserCredential.passwordHash` | 不可輸出 | `app_user_credentials` | `password_hash` | 不可輸出 | Security | 是 | KEEP | 內部安全欄位 |
| 密碼變更時間 | 密碼變更時間 | `AppUserCredential.passwordChangedAt` | 未輸出 | `app_user_credentials` | `password_changed_at` | 未使用 | Security | 否 | MISSING | 內部安全欄位 |
| 最後登入時間 | 最後登入時間 | `AppUserCredential.lastLoginAt` | 未輸出 | `app_user_credentials` | `last_login_at` | 未使用 | Security | 否 | MISSING | 內部安全欄位 |
| 角色代碼 | Security 權限 | `AppUserRole.roleCode` | `roles`（登入回應） | `app_user_roles` | `role_code` | `roles` | Security | 是 | KEEP | 只有 CASHIER、PAYMENT_OPERATOR |

### 4.2 公司、客戶與費用主檔

| 業務中文名稱 | 建議中文畫面名稱 | Backend Java Property | API JSON Field | DB Table | DB Column | Frontend TypeScript Property | 所層級 | 是否必填 | 狀態 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 公司 | 公司 | `Company` | `CompanyOption`／`company` | `companies` | `id`／`code`／`name`／`active` | `CompanyOption` | 公司主檔 | 是 | UI_LABEL_RENAME | Excel「帳冊表格所屬公司」過度限縮，應使用「公司」 |
| 客戶代號 | 客戶代號 | `Customer.code` | `code` | `customers` | `code` | `code` | 客戶主檔 | 是 | KEEP | 唯一，最多 50 |
| 客戶名稱 | 客戶名稱 | `Customer.name` | `name` | `customers` | `name` | `name` | 客戶主檔 | 是 | KEEP | 最多 200 |
| 預設請款類別 | 預設請款類別 | `Customer.defaultRequestCategory` | `defaultRequestCategory` | `customers` | `default_request_category` | `defaultRequestCategory` | 客戶主檔 | 否 | KEEP | EXPENSE／ADVANCE |
| 費用類型 | 費用類型 | `ExpenseType.code`／`name` | `expenseTypeCode`／`expenseTypeName` | `expense_types` | `code`／`name` | `expenseTypeCode`／`expenseTypeName` | 費用類型主檔 | 是 | UI_LABEL_RENAME | Excel「費用名稱」應對齊 ExpenseType |
| 計算類型 | 計算類型 | `ExpenseType.calculationType` | `calculationType` | `expense_types` | `calculation_type` | `calculationType` | 費用類型主檔 | 是 | KEEP | MANUAL、MEAL、QUANTITY_PRICE、TRAVEL、CONFIRMATION |
| 價格代碼 | 價格設定代碼 | `ExpensePriceSetting.priceCode` | `priceCode` | `expense_price_settings` | `price_code` | `priceCode` | 費用價格主檔 | 是 | KEEP | DEFAULT、NORMAL_MAIL 等 |
| 價格名稱 | 價格設定名稱 | `ExpensePriceSetting.priceName` | `priceName` | `expense_price_settings` | `price_name` | `priceName` | 費用價格主檔 | 是 | KEEP | 最多 100 |
| 單價 | 單價 | `ExpensePriceSetting.unitPrice` | `unitPrice` | `expense_price_settings` | `unit_price` | `unitPrice` | 費用價格主檔 | 是 | KEEP | NUMERIC(14,2) |
| 價格生效日／失效日 | 價格有效期間 | `effectiveFrom`／`effectiveTo` | `effectiveFrom`／`effectiveTo` | `expense_price_settings` | `effective_from`／`effective_to` | `effectiveFrom`／`effectiveTo` | 費用價格主檔 | 生效日是 | KEEP | LocalDate，有效日期查詢 |

### 4.3 請款主檔

| 業務中文名稱 | 建議中文畫面名稱 | Backend Java Property | API JSON Field | DB Table | DB Column | Frontend TypeScript Property | 所層級 | 是否必填 | 狀態 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 請款單號 | 請款單號 | `PaymentRequest.requestNo` | `requestNo` | `payment_requests` | `request_no` | `requestNo` | 請款主檔 | 是 | KEEP | V3 sequence 自動產生 |
| 申請人 | 申請人 | `PaymentRequest.applicant` | `applicant`／`applicantId` | `payment_requests` | `applicant_id` | `applicant`／`applicantId`／`applicantName` | 請款主檔 | 是 | KEEP | 建立草稿使用登入者 |
| 申請部門 | 申請部門 | `PaymentRequest.department` | `department`／`departmentId` | `payment_requests` | `department_id` | `department`／`departmentId`／`departmentName` | 請款主檔 | 是 | UI_LABEL_RENAME | Excel「部門」太寬泛 |
| 複核主管快照 | 複核主管快照 | `PaymentRequest.supervisorSnapshot` | Detail 現為 `supervisor`；Submit 為 `supervisorId`／`supervisorName` | `payment_requests` | `supervisor_snapshot_id` | `supervisor`／`supervisorId`／`supervisorName` | 請款主檔 | 送出後是 | API_BREAKING_CHANGE | 若將 Detail JSON `supervisor` 改為 `supervisorSnapshot` 會是 API breaking change；DB 不需改 |
| 公司 | 公司 | `PaymentRequest.company` | `company`／`companyId` | `payment_requests` | `company_id` | `company`／`companyId`／`companyName` | 請款主檔 | 是 | KEEP | 不應叫帳冊表格所屬公司 |
| 客戶 | 客戶 | `PaymentRequest.customer` | `customer`／`customerId` | `payment_requests` | `customer_id` | `customer`／`customerId`／`customerName` | 請款主檔 | 是 | KEEP | |
| 支出／代墊 | 請款類別 | `PaymentRequest.requestCategory` | `requestCategory` | `payment_requests` | `request_category` | `requestCategory` | 請款主檔 | 是 | KEEP | Excel 名稱可 UI 改成「請款類別」 |
| 請款事由 | 請款事由 | `PaymentRequest.reason` | `reason` | `payment_requests` | `reason` | `reason` | 請款主檔 | 是 | UI_LABEL_RENAME | Excel「事由」不夠明確 |
| 簽核狀態 | 簽核狀態 | `PaymentRequest.approvalStatus` | `approvalStatus` | `payment_requests` | `approval_status` | `approvalStatus` | 請款主檔 | 是 | KEEP | APPROVED 建議顯示「已核准」 |
| 付款狀態 | 付款狀態 | `PaymentRequest.paymentStatus` | `paymentStatus` | `payment_requests` | `payment_status` | `paymentStatus` | 請款主檔 | 是 | KEEP | 建立時預設 UNPAID，核准後仍維持 UNPAID |
| 請款總金額 | 請款總金額 | `PaymentRequest.totalAmount` | `totalAmount` | `payment_requests` | `total_amount` | `totalAmount` | 請款主檔 | 是 | KEEP | 與明細 `amount` 分開 |
| 送出時間 | 送出時間 | `PaymentRequest.submittedAt` | `submittedAt` | `payment_requests` | `submitted_at` | `submittedAt` | 請款主檔 | 否 | MISSING | Excel 欄位分頁未列 |
| 核准時間 | 核准時間 | `PaymentRequest.approvedAt` | `approvedAt` | `payment_requests` | `approved_at` | `approvedAt` | 請款主檔 | 否 | MISSING | 出納確認時間，不是付款時間 |
| 核准人 | 核准人／出納確認人 | `PaymentRequest.approvedBy` | `approvedBy` | `payment_requests` | `approved_by_id` | `approvedBy` | 請款主檔 | 否 | UI_LABEL_RENAME | 需要與付款登記人分開；權限為 CASHIER |
| 退回時間 | 退回時間 | `PaymentRequest.rejectedAt` | `rejectedAt` | `payment_requests` | `rejected_at` | `rejectedAt` | 請款主檔 | 否 | MISSING | |
| 結案時間 | 結案時間 | `PaymentRequest.closedAt` | `closedAt` | `payment_requests` | `closed_at` | `closedAt` | 請款主檔 | 否 | MISSING | |
| 實際付款時間 | 實際付款時間 | `PaymentRequest.paidAt` | `paidAt` | `payment_requests` | `paid_at` | `paidAt` | 請款主檔 | 付款後是 | KEEP | 不是 recordedAt |
| 付款登記人 | 付款登記人 | `PaymentRequest.paidBy` | `paidBy`／`paidById` | `payment_requests` | `paid_by_id` | `paidBy`／`paidById` | 請款主檔 | 付款後是 | UI_LABEL_RENAME | 權限為 PAYMENT_OPERATOR，不應只寫出納 |
| 付款方式 | 付款方式 | `PaymentRequest.paymentMethod` | `paymentMethod` | `payment_requests` | `payment_method` | `paymentMethod` | 請款主檔 | 否 | KEEP | CASH、BANK_TRANSFER、OTHER |
| 付款參考號碼 | 付款參考號碼 | `PaymentRequest.paymentReference` | `paymentReference` | `payment_requests` | `payment_reference` | `paymentReference` | 請款主檔 | 否 | KEEP | 最多 100；空白轉 NULL |
| 付款備註 | 付款備註 | `PaymentRequest.paymentNote` | `paymentNote` | `payment_requests` | `payment_note` | `paymentNote` | 請款主檔 | 否 | KEEP | TEXT；空白轉 NULL |
| 資料版本 | 資料版本 | `PaymentRequest.version` | `version` | `payment_requests` | `version` | `version` | 請款主檔 | 是 | KEEP | Optimistic Locking，Long／BIGINT |
| 建立／更新時間 | 建立時間／更新時間 | BaseTimeEntity `createdAt`／`updatedAt` | Detail 有 `createdAt`／`updatedAt` | `payment_requests` | `created_at`／`updated_at` | `createdAt`／`updatedAt` | 請款主檔 | 建立時是 | MISSING | 欄位分頁未列 |

### 4.4 請款明細與 extraData

| 業務中文名稱 | 建議中文畫面名稱 | Backend Java Property | API JSON Field | DB Table | DB Column | Frontend TypeScript Property | 所層級 | 是否必填 | 狀態 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 費用類型 | 費用類型 | `PaymentRequestItem.expenseType` | `expenseTypeId`／`expenseTypeCode`／`expenseTypeName` | `payment_request_items` | `expense_type_id` | 同名欄位 | 請款明細 | 是 | KEEP | |
| 單價設定快照來源 | 價格設定 | `PaymentRequestItem.priceSetting` | `priceSettingId`／`priceCode`／`priceName` | `payment_request_items` | `price_setting_id` | `priceSettingId`／`priceCode`／`priceName` | 請款明細 | 否 | KEEP | 人工輸入可為 NULL |
| 明細說明 | 明細說明 | `description` | `description` | `payment_request_items` | `description` | `description` | 請款明細 | 否 | UI_LABEL_RENAME | Excel「說明」可保留於明細畫面 |
| 人數 | 人數 | `peopleCount` | `peopleCount` | `payment_request_items` | `people_count` | `peopleCount` | 請款明細 | 依計算類型 | KEEP | MEAL 使用 |
| 天數 | 天數 | `days` | `days` | `payment_request_items` | `days` | `days` | 請款明細 | 依計算類型 | KEEP | MEAL 使用 |
| 餐數／數量 | 數量；MEAL 時顯示餐數 | `quantity` | `quantity` | `payment_request_items` | `quantity` | `quantity` | 請款明細 | 依計算類型 | UI_LABEL_RENAME | 不能固定叫餐數，也不能新增 mealCount |
| 單價快照 | 單價 | `unitPrice` | `unitPrice` | `payment_request_items` | `unit_price` | `unitPrice` | 請款明細 | 否 | UI_LABEL_RENAME | 這是請款當下保存的單價，不是前端輸入欄位 |
| 倍數 | 倍數 | `multiplier` | `multiplier` | `payment_request_items` | `multiplier` | `multiplier` | 請款明細 | 是 | OBSOLETE | Excel「來回*2」是舊描述；實際值可為 1、2 或其他合法倍數 |
| 明細金額 | 明細金額 | `amount` | `amount` | `payment_request_items` | `amount` | `amount` | 請款明細 | 是 | KEEP | 不與請款總金額混用 |
| 排序 | 明細排序 | `sortOrder` | `sortOrder` | `payment_request_items` | `sort_order` | `sortOrder` | 請款明細 | 是 | MISSING | Create form 自動產生，非使用者輸入 |
| 明細建立／更新時間 | 明細建立／更新時間 | BaseTimeEntity `createdAt`／`updatedAt` | Detail 未輸出 | `payment_request_items` | `created_at`／`updated_at` | 未使用 | 請款明細 | 建立時是 | MISSING |
| 起點 | 交通起點 | `extraData.startLocation`（Migration 註解示例，非固定 Java property） | `extraData.startLocation` 可承載 | `payment_request_items` | `extra_data` | `extraData` | extraData | 否 | AMBIGUOUS | 目前 Java／TypeScript 沒有固定 schema |
| 終點 | 交通終點 | `extraData.endLocation`（Migration 註解示例） | `extraData.endLocation` 可承載 | `payment_request_items` | `extra_data` | `extraData` | extraData | 否 | AMBIGUOUS | |
| 郵寄方式／郵資種類 | 郵寄方式 | `extraData.mailType`（Migration 註解示例） | `extraData.mailType` 可承載 | `payment_request_items` | `extra_data` | `extraData` | extraData | 依函證 | AMBIGUOUS | Excel「函證性質」可能混合郵資種類 |
| 印章大小 | 印章尺寸 | `extraData.stampSize`（Migration 註解示例） | `extraData.stampSize` 可承載 | `payment_request_items` | `extra_data` | `extraData` | extraData | 依費用類型 | AMBIGUOUS | Excel 有 LARGE／MEDIUM／SMALL 概念，未形成固定 DTO |
| 費用性質 | 待確認分類 | 未找到固定 Java property | 未找到固定 API field | `payment_request_items.extra_data` 可能承載 | `extra_data` | `extraData` | extraData | 未知 | AMBIGUOUS | 可能是應收／應付，也可能是費用分類；不可自行命名 |
| 函證性質 | 待確認函證業務種類 | 未找到固定 Java property | 未找到固定 API field | `payment_request_items.extra_data` 可能承載 | `extra_data` | `extraData` | extraData | 未知 | AMBIGUOUS | 不可自行新增 confirmationType 等欄位 |

### 4.5 簽核歷程與附件

| 業務中文名稱 | 建議中文畫面名稱 | Backend Java Property | API JSON Field | DB Table | DB Column | Frontend TypeScript Property | 所層級 | 是否必填 | 狀態 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 操作人 | 歷程操作人 | `ApprovalHistory.actor` | `actor` | `approval_histories` | `actor_id` | `actor` | 簽核歷程 | 是 | KEEP | AppUser |
| 操作動作 | 歷程動作 | `ApprovalHistory.action` | `action` | `approval_histories` | `action` | `action` | 簽核歷程 | 是 | KEEP | SUBMIT、MANAGER_*、CASHIER_*、PAYMENT_RECORDED |
| 前簽核狀態／後簽核狀態 | 狀態變更 | `fromApprovalStatus`／`toApprovalStatus` | 同名 JSON | `approval_histories` | `from_approval_status`／`to_approval_status` | 同名 property | 簽核歷程 | 否 | KEEP | |
| 前付款狀態／後付款狀態 | 付款狀態變更 | `fromPaymentStatus`／`toPaymentStatus` | 同名 JSON | `approval_histories` | `from_payment_status`／`to_payment_status` | 同名 property | 簽核歷程 | 否 | KEEP | |
| 歷程備註 | 歷程備註 | `comment` | `comment` | `approval_histories` | `comment` | `comment` | 簽核歷程 | 否 | KEEP | 付款歷程通常使用 paymentNote |
| 操作時間 | 操作時間 | `actedAt` | `actedAt` | `approval_histories` | `acted_at` | `actedAt` | 簽核歷程 | 是 | KEEP | OffsetDateTime |
| 附件類型 | 附件類型 | `PaymentRequestAttachment.attachmentType` | `attachmentType` | `payment_request_attachments` | `attachment_type` | `attachmentType` | 附件 | 是 | KEEP | INVOICE、RECEIPT、REQUEST_PROOF、PAYMENT_PROOF、OTHER |
| 原始檔名 | 原始檔名 | `originalFilename` | `originalFilename` | `payment_request_attachments` | `original_filename` | `originalFilename` | 附件 | 是 | KEEP | 使用者可見 |
| 儲存檔名 | 儲存檔名 | `storedFilename` | 不回傳 | `payment_request_attachments` | `stored_filename` | 未使用 | 附件 | 是 | KEEP | 內部儲存欄位 |
| 儲存路徑 | 儲存路徑 | `storagePath` | 不回傳 | `payment_request_attachments` | `storage_path` | 未使用 | 附件 | 是 | KEEP | 一般使用者不應直接顯示 |
| MIME 類型 | 檔案類型 | `contentType` | `contentType` | `payment_request_attachments` | `content_type` | `contentType` | 附件 | 是 | KEEP | |
| 檔案大小 | 檔案大小 | `fileSize` | `fileSize` | `payment_request_attachments` | `file_size` | `fileSize` | 附件 | 是 | KEEP | Long／BIGINT |
| 上傳者 | 上傳者 | `uploadedBy` | Detail 目前未輸出 uploadedBy | `payment_request_attachments` | `uploaded_by_id` | 未使用 | 附件 | 是 | MISSING | Entity 與 Migration 有欄位，Detail DTO 未回傳 |
| 建立時間 | 附件建立時間 | `createdAt` | `createdAt` | `payment_request_attachments` | `created_at` | `createdAt` | 附件 | 是 | KEEP | 不繼承 BaseTimeEntity |

### 4.6 API 分頁、Master Data、Security 與報表

| 業務中文名稱 | 建議中文畫面名稱 | Backend Java Property | API JSON Field | DB Table | DB Column | Frontend TypeScript Property | 所層級 | 是否必填 | 狀態 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 列表內容 | 列表內容 | `PaymentRequestPageResponse.content` | `content` | 無 | 無 | `content` | 純畫面顯示欄位 | 是 | KEEP | 分頁包裝欄位 |
| 頁碼／筆數 | 頁碼／每頁筆數 | `page`／`size` | `page`／`size` | 無 | 無 | `page`／`size` | 純畫面顯示欄位 | 是 | KEEP | |
| 總筆數／總頁數 | 總筆數／總頁數 | `totalElements`／`totalPages` | 同名 JSON | 無 | 無 | 同名 property | 純畫面顯示欄位 | 是 | KEEP | |
| 首頁／末頁 | 首頁／末頁 | `first`／`last` | 同名 JSON | 無 | 無 | 同名 property | 純畫面顯示欄位 | 是 | KEEP | |
| 公司下拉選項 | 公司 | `CompanyOptionResponse` | `id`／`code`／`name` | `companies` | 同名欄位 | `CompanyOption` | 公司主檔 | 是 | KEEP | |
| 客戶下拉選項 | 客戶 | `CustomerOptionResponse` | `id`／`code`／`name`／`defaultRequestCategory` | `customers` | 同名欄位 | `CustomerOption` | 客戶主檔 | 是 | KEEP | |
| 費用類型下拉選項 | 費用類型 | `ExpenseTypeOptionResponse` | `id`／`code`／`name`／`calculationType` | `expense_types` | 同名欄位 | `ExpenseTypeOption` | 費用類型主檔 | 是 | KEEP | |
| 有效價格下拉選項 | 價格設定 | `ExpensePriceOptionResponse` | `id`／`priceCode`／`priceName`／`unitPrice`／有效日 | `expense_price_settings` | 同名欄位 | `ExpensePriceOption` | 費用價格主檔 | 是 | KEEP | |
| 登入帳密 | 登入帳號／密碼 | `LoginRequest.username`／`password` | `username`／`password` | 無 | 無 | `LoginRequest` | Security | 是 | KEEP | 密碼不可進入業務欄位 |
| 登入使用者 | 目前登入者 | `AuthenticatedUserResponse` | `userId`／`username`／`displayName`／`roles` | `app_users`／`app_user_roles` | `id`／`role_code` | `AuthenticatedUser` | Security | 是 | KEEP | |
| CSRF Token | CSRF Token | `CsrfTokenResponse` | `token`／`headerName`／`parameterName` | 無 | 無 | `CsrfTokenResponse` | Security | 是 | KEEP | |
| 客戶代號 | 客戶代號 | 未建立報表 DTO | 報表輸出欄位 | 無 | 無 | 未建立 | 報表輸出欄位 | 依報表 | REPORT_ONLY | Excel 結果檔案；不是 PaymentRequest 完整欄位 |
| 客戶名稱 | 客戶名稱 | 未建立報表 DTO | 報表輸出欄位 | 無 | 無 | 未建立 | 報表輸出欄位 | 依報表 | REPORT_ONLY | |
| 費用名稱 | 費用類型 | 未建立報表 DTO | 報表輸出欄位 | 無 | 無 | 未建立 | 報表輸出欄位 | 依報表 | REPORT_ONLY | 建議報表使用「費用類型」；Excel 舊名仍保留 |
| 總金額 | 報表總金額 | 未建立報表 DTO | 報表輸出欄位 | 無 | 無 | 未建立 | 報表輸出欄位 | 依報表 | AMBIGUOUS | 未確認是客戶／費用類型彙總或單張請款總額 |

## 5. 主要差異與問題

### 5.1 Excel 與 Migration 不一致

1. `payment_status` 在 V2 建立 PaymentRequest 時即有 `DEFAULT 'UNPAID'`；不是「核准時自動建立」。核准後仍維持 UNPAID，付款登記後才變成 PAID。
2. V2 實際欄位是 `request_category`、`reason`、`supervisor_snapshot_id`、`approved_by_id`、`paid_by_id`、`payment_reference`、`payment_note`、`version` 等，Excel 欄位分頁沒有完整列出。
3. Excel「來回*2」不是 V2 欄位；實際欄位是可泛用的 `multiplier NUMERIC(10,2)`。
4. V2 `extra_data` 是 JSONB。Migration 註解只提供 `startLocation`、`endLocation`、`stampSize`、`mailType` 示例，不代表固定 schema。
5. 附件是 `payment_request_attachments` 關聯表，不是 PaymentRequest 的單一文字欄位。
6. V4 的 Security Role 只有 `CASHIER`、`PAYMENT_OPERATOR`；Excel 將付款登記與出納確認都稱為出納，語意不足。

### 5.2 Excel 與 Backend DTO 不一致

1. Backend 有 `approvalStatus`、`paymentStatus`、`submittedAt`、`approvedAt`、`approvedBy`、`rejectedAt`、`closedAt`、`paidAt`、`paidBy`、`paymentMethod`、`paymentReference`、`paymentNote`、`version`，Excel 欄位分頁未完整表達。
2. `CreatePaymentDraftItemRequest` 使用 `priceCode`、`multiplier`、`manualAmount`、`extraData`、`sortOrder`；Excel 只有「單價」與「來回*2」等舊描述。
3. `PaymentRequestDetailResponse` 的欄位名是 `supervisor`，但語意實際為 `supervisorSnapshot`。直接改成 `supervisorSnapshot` 會是 `API_BREAKING_CHANGE`，本階段不改。
4. `RecordPaymentRequest` 已不接受 `paidById`；付款人由登入 Principal 決定，Excel 的單一「出納」需拆為核准人與付款登記人。
5. `PaymentRequestAttachment` 的 `uploadedBy` 存在於 Entity／DB，但目前 Detail Response 沒有輸出，標記 `MISSING`。

### 5.3 Excel 與 Frontend 不一致

1. Backend／Frontend 核心 property 大多已對齊 camelCase，但建立草稿頁仍使用英文 labels：`Company`、`Customer`、`Category`、`Reason`、`Items`、`Save draft`；可安全改為中文 UI labels，不涉及 API。
2. 列表中的 `supervisorName` 目前畫面顯示「主管」，建議改成「複核主管快照」或「複核主管」，避免被理解為即時主管。
3. Detail 已顯示「主管快照」、「核准人」、「付款人」，與拆分後的業務語意較接近。
4. Item Editor 已使用「倍數」，因此不應再使用 Excel 的「來回*2」作為欄位名。
5. Item Editor 對 `MEAL` 將 `quantity` 顯示為「數量」，需求規格建議在 MEAL 情境顯示「餐數」；這是 `UI_LABEL_RENAME`，不需改 API。
6. `CreatePaymentDraftItemRequest.extraData` 目前由建立頁送 `null`，前端沒有固定起點、終點、郵寄方式、函證分類或印章大小的欄位，不能從 Excel 推定要新增。

## 6. 安全可修改的 UI Label

不改 property、API 或 DB 的前提下，下一階段可優先處理：

- `Create payment draft` → `新增請款草稿`
- `Company` → `公司`
- `Customer` → `客戶`
- `Category` → `請款類別`
- `Reason` → `請款事由`
- `Items` → `請款明細`
- `Save draft` → `儲存草稿`
- `Request no.` → `請款單號`
- `Approval` → `簽核狀態`
- `Payment` → `付款狀態`
- `Total` → `請款總金額`
- `supervisorName` 的列表欄名「主管」→「複核主管快照」
- MEAL 的 `quantity` label「數量」→「餐數」
- `APPROVED` 顯示「核准結案」→「已核准」
- 付款登記頁的角色提示「出納」→「付款登記人」或「PAYMENT_OPERATOR」

## 7. 需要確認的 Rename

### 7.1 API breaking change

- `PaymentRequestDetailResponse.supervisor` → `supervisorSnapshot`：語意較精準，但會改變既有 Detail JSON，標記 `API_BREAKING_CHANGE`，需前後端一起版本化後才可做。
- 若將 `PaymentRequestListItemResponse.supervisorId`／`supervisorName` 改成 `supervisorSnapshotId`／`supervisorSnapshotName`，同樣屬 API breaking change。

### 7.2 DB migration required

本次沒有發現必須立即改 DB column 的項目。V1～V4 的 DB 名稱與目前 Entity mapping 已對齊。若未來把 `paid_by_id` 改名為 `payment_operator_id`，會是 `DB_MIGRATION_REQUIRED`，但目前不建議，因為 `paidBy` 是清楚且通用的付款登記人語意。

## 8. 已廢棄、模糊與缺少欄位

### 已廢棄或不應再沿用

- `來回*2`：由 `multiplier` 取代，不是固定欄位。
- `APPROVED = 核准結案`：容易誤導已付款，建議只顯示「已核准」。
- 單一「出納」：不能同時代表 `CASHIER` 與 `PAYMENT_OPERATOR`。
- `費用名稱`：在請款明細與報表中應優先使用「費用類型」。

### 模糊欄位

- `費用性質`：可能是應收／應付分類，也可能是費用類型的一部分。
- `函證性質`：可能是郵寄方式、函證業務種類，或應收／應付分類。
- `結果檔案.總金額`：彙總粒度未定義。
- `帳冊表格所屬公司`：與 Company 通用語意不一致，但不是 DB 欄位錯誤。

### Backend／DB 已有但 Excel 或 API 尚未完整呈現

- `createdAt`、`updatedAt`。
- `submittedAt`、`approvedAt`、`approvedBy`、`rejectedAt`、`closedAt`。
- `version`。
- `AppUser.email`、active flags。
- `PaymentRequestAttachment.uploadedBy`、`storedFilename`、`storagePath`。
- Security credential timestamps 與 role relationship。
- 列表／分頁的 `first`、`last`、`totalElements`、`totalPages` 是 API／畫面欄位，不是 DB 欄位。

## 9. approvedBy／paidBy 拆分結果

| 概念 | Backend | DB | 權限 | 業務意義 |
| --- | --- | --- | --- | --- |
| 核准人 | `approvedBy`／`approvedById` | `approved_by_id` | `CASHIER` | 出納確認，使 `PENDING_CASHIER → APPROVED`；不代表已付款 |
| 付款登記人 | `paidBy`／`paidById` | `paid_by_id` | `PAYMENT_OPERATOR` | 將 `APPROVED/UNPAID → APPROVED/PAID` |

兩者可由同一 AppUser 擔任，但系統權限不等價，不應在 Excel、UI 或文件合併成單一「出納」。

## 10. amount、totalAmount、quantity 與 meal count

- `PaymentRequestItem.amount`：單一明細金額。
- `PaymentRequest.totalAmount`：請款單全部明細加總後的主檔總金額。
- `PaymentRequestItem.quantity`：通用數量欄位；MEAL 時其中文 label 建議為「餐數」，其他數量型顯示「數量」。
- 不新增 `mealCount`，因為 V2 與目前 Entity／DTO 使用的是 `quantity`。
- `multiplier` 是通用倍數；不要以 `來回*2` 命名或假設一定為 2。

## 11. extraData 結論

目前實際證據只有：V2 Migration 註解示例 `startLocation`、`endLocation`、`stampSize`、`mailType`；Java Entity 使用 `Map<String,Object>`，Frontend 使用 `Record<string, unknown>`，沒有固定欄位驗證或表單欄位。

因此：

- 起點／終點可暫列交通資料候選欄位。
- `mailType` 可暫列郵寄方式／郵資種類候選欄位。
- `stampSize` 可暫列印章尺寸候選欄位。
- Excel 的「費用性質」、「函證性質」仍為 `AMBIGUOUS`，不可自行新增 `confirmationType`、`confirmationNature`、`mailCategory` 等 Java／API 欄位。

## 12. 本階段產出與變更界線

- 新增：`docs/payment-field-alignment.md`。
- 新增：`請款流程_欄位對齊版.xlsx`，保留原始三個分頁並新增「欄位對照表」。
- 未修改任何正式 Java／Vue 業務程式。
- 未修改 V1～V4、未建立 V5、未修改 Entity、Repository、SecurityConfig、Database 或 API Contract。
- 未執行案件 14／20 的付款 PostgreSQL E2E；本階段只做規格盤點。

## 13. 建議下一階段優先順序

1. 先確認 `費用性質`、`函證性質` 的業務定義與 extraData schema；在確認前不要新增程式欄位。
2. 先做不涉及契約的 UI label 對齊，尤其是 `APPROVED`、`出納`、英文建立草稿表單、MEAL 的餐數顯示。
3. 確認是否要以新 API 版本處理 `supervisor` → `supervisorSnapshot`，不要直接改既有 JSON。
4. 確認報表「總金額」的彙總粒度後，才設計 Report DTO／查詢。
5. 再盤點附件上傳 API 是否需要補 `uploadedBy` 顯示與付款證明流程。

