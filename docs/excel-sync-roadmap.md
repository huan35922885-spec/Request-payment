# Excel 邏輯同步路線圖（契約基線）

文件日期：2026-08-06

本文件以 repo 內 `請款流程_已完成開發規則.xlsx` 為產品契約，記錄與程式對齊的決策與驗收矩陣。  
取代或補充 [`payment-attachment-design.md`](payment-attachment-design.md) 中與付款證明、角色相衝突的舊描述。

## 1. 簽核動作者

| 動作 | 決定方式 | Security Role |
| --- | --- | --- |
| 申請、草稿、一般附件 | 登入申請人 | 無專用 Role |
| 部門主管複核／退回 | `department_supervisors` + 送出時 `supervisorSnapshot` | 無 `MANAGER` Role |
| 出納確認／退回、付款登記、付款資料與付款證明維護 | 具 `CASHIER` | `CASHIER` |
| 費用主檔後台 | 具 `MASTER_DATA_ADMIN` | 非簽核流程 |

**廢止：** `PAYMENT_OPERATOR` 不再作為簽核或付款權限；資料庫 V10 合併至 `CASHIER`。

## 2. 狀態與流程（與 Excel「簽核流程規則」一致）

- `DRAFT` → `PENDING_MANAGER` → `PENDING_CASHIER` → `APPROVED` + `UNPAID` → `APPROVED` + `PAID`
- 主管或出納退回：`REJECTED_CLOSED`，不可重新送審
- 出納確認代表核准，不代表已付款
- `approvalStatus` 與 `paymentStatus` 分開；登記付款後 `approvalStatus` 維持 `APPROVED`

**`payment_status` 語意：** DB 自草稿起預設 `UNPAID`（V2）。業務上「可付款」以 `APPROVED` + `UNPAID` 表示；Excel「核准時建立」以文件解讀為出納確認後進入可付款態，不另改 V2 default。

## 3. 付款證明（B1）

| 情境 | 角色 | 行為 |
| --- | --- | --- |
| 草稿／送審前 | 申請人 | 一般附件（發票等），**不可**上傳 `PAYMENT_PROOF` |
| 首次登記付款 `UNPAID→PAID` | `CASHIER` | `record-payment` multipart，**至少 1 檔**，可 **多檔** `PAYMENT_PROOF` |
| `APPROVED` + `PAID` 維護 | `CASHIER` | `PATCH /payment` 更新付款欄位；`POST/DELETE /payment-proofs` 增刪證明 |
| 下載 | 依請款讀取授權 | 與現有 attachment download 相同 |

請款主檔與明細在核准或退回後**不可修改**；僅付款相關欄位與付款證明可維護。

## 4. API 契約摘要（實作後）

| 方法 | 路徑 | 權限 |
| --- | --- | --- |
| POST | `/api/payment-requests/{id}/record-payment` | `CASHIER`，multipart `request` + `files` |
| PATCH | `/api/payment-requests/{id}/payment` | `CASHIER`，`APPROVED`（含已 `PAID`） |
| POST | `/api/payment-requests/{id}/payment-proofs` | `CASHIER`，multipart |
| DELETE | `/api/payment-requests/{id}/payment-proofs/{attachmentId}` | `CASHIER` |
| GET | `/api/payment-requests/export/result` | `CASHIER`，Excel 結果檔 |

## 5. 結果檔案匯出粒度

依 Excel「結果檔案」分頁：彙總欄位為客戶代號、客戶名稱、費用類型名稱、總金額。  
預設彙總：**同一查詢期間內，依客戶 + 費用類型加總請款明細 `amount`**（非單張請款單一列）。

## 6. 階段驗收

後續 PR 須對照本文件 §3 矩陣與 §4 API 逐項測試。
