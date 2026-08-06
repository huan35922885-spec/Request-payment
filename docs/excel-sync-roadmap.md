# Excel 邏輯同步路線圖（契約基線）

文件日期：2026-08-06

本文件以 repo 內 `請款流程_已完成開發規則.xlsx` 為產品契約，記錄與程式對齊的決策與驗收矩陣。  
取代或補充 [`payment-attachment-design.md`](payment-attachment-design.md)、[`security-authorization-baseline.md`](security-authorization-baseline.md)、[`payment-read-authorization-design.md`](payment-read-authorization-design.md)、[`payment-field-alignment.md`](payment-field-alignment.md) 中與付款證明、角色相衝突的舊描述。

## 1. 簽核動作者

| 動作 | 決定方式 | Security Role |
| --- | --- | --- |
| 申請、草稿、一般附件 | 登入申請人 | 無專用 Role |
| 部門主管複核／退回 | `department_supervisors` + 送出時 `supervisorSnapshot` | 無 `MANAGER` Role |
| 出納確認／退回、付款登記、付款資料與付款證明維護 | 具 `CASHIER` | `CASHIER` |
| 費用主檔後台 | 具 `MASTER_DATA_ADMIN` | 非簽核流程 |

**廢止：** `PAYMENT_OPERATOR` 不再作為簽核或付款權限；資料庫 V10 合併至 `CASHIER`。

### V10 上線檢查

1. 遷移會：僅有 `PAYMENT_OPERATOR` 者補 `CASHIER` → 刪除所有 `PAYMENT_OPERATOR` 列 → CHECK 僅允許 `CASHIER`、`MASTER_DATA_ADMIN`。
2. 上線後抽樣登入：`/api/auth/me` 的 `roles` **不得**再含 `PAYMENT_OPERATOR`。
3. 確認種子／人工帳號：出納帳號具備 `CASHIER`；主檔維護者具備 `MASTER_DATA_ADMIN`。

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
| POST | `/api/payment-requests/{id}/record-payment` | `CASHIER`，multipart `request` + `files`（相容 `file`） |
| PATCH | `/api/payment-requests/{id}/payment` | `CASHIER`，僅已 `PAID` |
| POST | `/api/payment-requests/{id}/payment-proofs` | `CASHIER`，multipart `files` |
| DELETE | `/api/payment-requests/{id}/payment-proofs/{attachmentId}` | `CASHIER` |
| GET | `/api/payment-reports/result-export` | `CASHIER`，Excel 結果檔（`paidFrom`／`paidTo`） |

## 5. 結果檔案匯出

依 Excel「結果檔案」分頁：客戶代號、客戶名稱、費用名稱、總金額。  
彙總：**期間內已付款案件，依客戶 + 費用類型加總明細 `amount`**。

**技術：** 現況為 **Apache POI `.xlsx` MVP**。Excel 另寫 Jasper／iReport；若客戶強制報表範本引擎，再另開工作以 Jasper 取代匯出實作（API 路徑與欄位契約維持不變）。

## 6. 前端欄位用語（Excel「欄位」分頁）

畫面優先用語：請款單號、客戶代號／名稱、支出／代墊、公司、部門、事由、費用名稱、起點、終點、費用性質、函證性質、人數、天數、數量／餐數、印章大小、單價、來回倍數、請款金額、申請人、複核人、出納、附件。

`extraData` 約定（前端寫入）：

| 鍵 | 用途 |
| --- | --- |
| `startLocation`／`endLocation` | 交通起點／終點 |
| `expenseNature` | 應收／應付 |
| `mailType` | 平信／掛號／限掛／其他 |
| `stampSize` | 大／中／小 |

## 7. 手動 E2E 驗收清單

- [ ] 申請人建立草稿（含餐費／交通／函證／印章相關欄位），上傳一般附件，送出
- [ ] 主管（快照對象）核准 → 待出納；另一帳號不可主管核准
- [ ] 出納確認 → `APPROVED`／`UNPAID`；畫面顯示「核准結案」
- [ ] 僅 `CASHIER` 可見待付款列表與登記表單；multipart ≥1 檔（可多檔）登記 → `PAID`
- [ ] 已付款：出納更新付款日期／方式／備註；增刪付款證明；請款事由與明細不可改
- [ ] 結果檔匯出含四欄；期間篩選正確
- [ ] `MASTER_DATA_ADMIN` 可維護費用類型與單價；無此角色無法進後台頁
- [ ] 登入 `roles` 無 `PAYMENT_OPERATOR`

## 8. 階段驗收

PR／上線須對照本文件 §3 矩陣、§4 API、§7 清單。自動化回歸：`backend` `mvnw test`、`frontend` `npm run build`。
