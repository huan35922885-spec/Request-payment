# 請款資料 Read Authorization 現況盤點與權限矩陣設計

> **Superseded（付款讀取角色）：** `PAYMENT_PENDING`／付款詳情讀取改為僅 `CASHIER`；見 [`excel-sync-roadmap.md`](excel-sync-roadmap.md)。下文 `PAYMENT_OPERATOR` 為歷史設計敘述。
>
> 本文件是設計與盤點產出，不代表本階段已完成授權實作。
>
> 本階段只讀取現有 Java、Vue、Migration、測試與 Git 狀態；沒有修改 SecurityConfig、Controller、Service、Repository、DTO、Entity、Vue、Router、Migration 或資料庫。

## 1. 現況摘要

目前後端已有 Session、CSRF、Username／Password 登入，以及兩個資料庫角色：`CASHIER`、`PAYMENT_OPERATOR`。目前已完成的寫入授權如下：

| 寫入用途 | 現有授權方式 |
| --- | --- |
| 建立草稿 | Controller 以 `AuthenticatedUserPrincipal.userId` 傳入 Applicant |
| 送出草稿 | Service 驗證案件 Applicant 等於登入 user id |
| 主管核准／退回 | Service 驗證登入 user id 等於 `supervisorSnapshot.id` |
| 出納核准／退回 | Security 要求 `CASHIER`，Service 使用登入 user id |
| 登記付款 | Security 要求 `PAYMENT_OPERATOR`，Service 使用登入 user id |

Read Authorization 尚未完成。`SecurityConfig` 對未特別列出的路徑使用 `.anyRequest().permitAll()`，因此目前兩支 Payment GET 以及四支 Master Data GET 都沒有登入、案件關係或角色範圍限制。`PaymentRequestController` 的 GET 方法也沒有接收 `AuthenticatedUserPrincipal`。

最重要的目前風險是：

1. 知道案件 id 即可直接讀取 Detail。
2. List 接受 `applicantId`、`supervisorId` 等外部查詢參數，Service 只驗證格式與正數，不驗證參數是否等於登入者。
3. Manager Queue 目前把登入者 id 放入 `supervisorId`，並在前端再次過濾；Browser 可以自行修改 Query Parameter。
4. List 與 Detail 沒有共用 Read Authorization 規則，可能形成「List 查不到但 Detail 查得到」或反向不一致。

本文件的建議原則是：後端以登入 Principal 決定資料範圍，外部 user id 只可作為受控的內部查詢值，不能用來宣告「這是我的案件」。

## 2. 目前所有 GET API

### 2.1 Payment GET API

| HTTP API | Controller method | Service method | Repository 查詢 | 目前是否 permitAll | 是否使用 Principal | Response | 目前前端使用 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /api/payment-requests` | `PaymentRequestController.list` | `ListPaymentRequestsService.list` | `PaymentRequestRepository.search` JPQL + `PageRequest` | 是，落入 `anyRequest().permitAll()` | 否 | `PaymentRequestPageResponse`，包含列表、分頁資訊 | Manager、Cashier、Payment Queue |
| `GET /api/payment-requests/{id}` | `PaymentRequestController.getDetail` | `GetPaymentRequestDetailService.getDetail` | `PaymentRequestRepository.findById`，再查 Item、History、Attachment Repository | 是，落入 `anyRequest().permitAll()` | 否 | `PaymentRequestDetailResponse` | 建立草稿後、Manager、Cashier、Payment Detail |

`GET /api/payment-requests` 的 Controller 實際接受：

```text
page, size, requestNo, approvalStatus, paymentStatus, requestCategory,
applicantId, departmentId, supervisorId, companyId, customerId,
createdFrom, createdTo
```

List Service 目前只做頁碼、大小、日期範圍、ID 正數與 requestNo 空白正規化；沒有任何登入者範圍驗證。

Detail Service 目前流程是：先依 id 查主檔，再依 id 讀取明細、Approval History、附件，最後直接組成 Response；沒有 Applicant、Supervisor Snapshot、CASHIER 或 PAYMENT_OPERATOR 檢查。

### 2.2 Master Data GET API

| HTTP API | Controller method | Service／Repository | 目前是否 permitAll | Response | 目前前端使用 |
| --- | --- | --- | --- | --- | --- |
| `GET /api/master/companies` | `MasterDataController.getCompanies` | `MasterDataQueryService.getCompanies` → active companies | 是 | `id`, `code`, `name` | 建立草稿表單 |
| `GET /api/master/customers` | `MasterDataController.getCustomers` | `MasterDataQueryService.getCustomers` → active customers | 是 | `id`, `code`, `name`, `defaultRequestCategory` | 建立草稿表單 |
| `GET /api/master/expense-types` | `MasterDataController.getExpenseTypes` | `MasterDataQueryService.getExpenseTypes` → active expense types | 是 | `id`, `code`, `name`, `calculationType` | 建立草稿表單 |
| `GET /api/master/expense-types/{expenseTypeId}/prices` | `MasterDataController.getExpensePrices` | `MasterDataQueryService.getExpensePrices` → 有效日期價格 | 是 | `id`, `priceCode`, `priceName`, `unitPrice`, `effectiveFrom`, `effectiveTo` | 建立草稿表單 |

Master Data 目前沒有個人案件資料，但其內容屬於內部業務主檔；最低建議是先要求 `authenticated()`，本階段不決定更細的角色限制。

### 2.3 其他 GET Security API

| HTTP API | 目前規則 |
| --- | --- |
| `GET /api/auth/csrf` | permitAll，提供前端 CSRF Token |
| `GET /api/auth/me` | authenticated |

## 3. Frontend 頁面與 API 對照

| 頁面／Route | 目前使用的讀取 API | 目前查詢條件或行為 | 目前問題 |
| --- | --- | --- | --- |
| `HomeView.vue`、`/` | 無案件 GET；提供導向連結 | 受全域登入 Route Guard 保護 | 不是案件讀取頁 |
| `PaymentRequestCreateView.vue`、`/payment-requests/new` | Master Data 四支 GET | 讀取 active 主檔；成功建立後導向一般 Detail | 一般 Detail 目前只靠 id，後端未做案件授權 |
| `ManagerPendingListView.vue`、`/manager/payment-requests` | `GET /api/payment-requests` | `approvalStatus=PENDING_MANAGER`、`supervisorId=authStore.user.userId`、page、size；前端再以 `supervisorId` 過濾 | `supervisorId` 可被 Browser 改寫；後端不信任登入者關係 |
| Manager Detail、`/manager/payment-requests/:id` | `GET /api/payment-requests/{id}` | UI 以 `detail.supervisor.id === current user id` 決定是否顯示操作按鈕 | 讀取本身沒有後端授權；UI 條件不能取代 API 授權 |
| `CashierPendingListView.vue`、`/cashier/payment-requests` | `GET /api/payment-requests` | `approvalStatus=PENDING_CASHIER`、page、size | 現在任何登入者都能呼叫相同 GET；UI 只有提示 `CASHIER` |
| Cashier Detail、`/cashier/payment-requests/:id` | `GET /api/payment-requests/{id}` | UI 以 `CASHIER` 與狀態決定操作按鈕 | Detail 讀取沒有角色或案件範圍授權 |
| `PaymentPendingListView.vue`、`/payment/payment-requests` | `GET /api/payment-requests` | `approvalStatus=APPROVED`、`paymentStatus=UNPAID`、page、size | `PAYMENT_OPERATOR` 只在 UI 判斷，GET 本身未限制 |
| Payment Detail、`/payment/payment-requests/:id` | `GET /api/payment-requests/{id}` | UI 以 `PAYMENT_OPERATOR`、APPROVED、UNPAID 決定付款表單 | 知道 id 的其他人仍可直接讀取完整 Detail |
| 一般 Detail、`/payment-requests/:id` | `GET /api/payment-requests/{id}` | 建立草稿成功後查看；送出後仍可重新查看 | 目前沒有 Applicant ownership check |

目前 `frontend/src/api/paymentRequestApi.ts` 的 TypeScript List Query type 只列出 `page`、`size`、`requestNo`、`approvalStatus`、`paymentStatus`、`supervisorId`；後端 Controller 實際支援的 `requestCategory`、`applicantId`、`departmentId`、`companyId`、`customerId`、`createdFrom`、`createdTo` 未完整反映在此 TypeScript type。這是契約盤點結果，不在本階段修改。

## 4. 目前安全缺口

### 4.1 GET 路徑缺少登入限制

目前 `SecurityConfig` 只有登入、CSRF、`/api/auth/me`／logout、建立草稿與各寫入 endpoint 有明確規則；兩支 Payment GET、四支 Master Data GET 都落入 `.anyRequest().permitAll()`。

Payment Detail Response 至少包含：

- Applicant 的 id、username、displayName。
- Department code、name。
- Supervisor Snapshot 的 id、username、displayName。
- Company、Customer 的 code、name。
- 請款事由與總金額。
- 明細的費用類型、價格設定快照、數量、單價、倍數、金額、`extraData` JSONB。
- Approval History 的操作人、狀態前後值、備註與時間。
- 付款時間、付款登記人、付款方式、付款參考號碼、付款備註。
- 附件原始檔名、MIME type、檔案大小與建立時間。

這些資料分別涉及個人資料、客戶資料、財務資料、付款資訊與內部流程資訊。雖然目前 Detail 沒有回傳 `storagePath`，仍不應讓未登入者依 id 讀取。

### 4.2 List 可被任意篩選

`applicantId`、`supervisorId` 是最直接的越權風險。即使未來前端把它們放成登入者 id，Browser 仍可改成別的 id。`companyId`、`customerId`、`departmentId` 等也可能使一般登入者枚舉其他部門或客戶案件，不能只以「是合法數字」視為安全。

### 4.3 UI 條件不等於授權

目前 Vue 以 role、status、Applicant id 或 Supervisor Snapshot id 控制按鈕與表單顯示，但 GET API 已在畫面載入前執行。前端條件只能改善使用體驗，不能保護 API 或敏感 Response。

## 5. Query Parameter 盤點與 Spoofing 風險

| Filter | Backend 實際支援 | 一般使用者可否任意指定 | 建議處理 | 目前 Frontend 使用 | 越權風險 |
| --- | --- | --- | --- | --- | --- |
| `requestNo` | 是，模糊包含比對 | 不應直接視為權限 | 在已授權 scope 內再套用；不要用它繞過 scope | 目前 TypeScript 有欄位，但現有三個 Queue 未使用 | 中：可枚舉已知單號或縮小其他人的結果 |
| `approvalStatus` | 是 | Queue 可提出固定值，但不應放大範圍 | 由 scope 決定允許的狀態；外部值只能是 scope 內子篩選 | Manager、Cashier、Payment Queue 使用 | 高：可改查其他狀態 |
| `paymentStatus` | 是 | 同上 | 由 scope 決定；付款範圍不可由一般使用者自行放寬 | Payment Queue 使用 | 高 |
| `requestCategory` | 是 | 未確認是否為一般篩選 | 先記錄為未確認；未來只能在已授權資料集內套用 | Backend 支援，現有 Payment Queue 未使用 | 中 |
| `applicantId` | 是 | 不可用來宣告「我的案件」 | `MY_REQUESTS` 強制覆蓋為 Principal user id；管理查詢另行授權 | Backend 支援，現有頁面未使用 | 極高 |
| `departmentId` | 是 | 未確認部門互閱規則 | 未來需明確 scope；一般 Applicant 不應以它擴張範圍 | Backend 支援，現有頁面未使用 | 高 |
| `supervisorId` | 是 | 不可信任 | Manager scope 強制使用 Principal user id；外部值移除或只供管理端點 | Manager Queue 使用 current user id | 極高 |
| `companyId` | 是 | 未確認跨公司查詢規則 | 只能在已授權 scope 內使用；不作為授權依據 | Backend 支援，現有頁面未使用 | 中至高 |
| `customerId` | 是 | 未確認客戶可見範圍 | 只能在已授權 scope 內使用 | Backend 支援，現有頁面未使用 | 中至高 |
| `createdFrom` | 是 | 日期不是授權 | 只能縮小已授權資料集 | Backend 支援，現有頁面未使用 | 低至中 |
| `createdTo` | 是 | 日期不是授權 | 只能縮小已授權資料集 | Backend 支援，現有頁面未使用 | 低至中 |
| `page` | 是 | 可以指定 | 僅影響已授權結果；限制合法頁碼 | 三個 Queue 使用 | 不應造成越權，但可能造成枚舉 |
| `size` | 是，1～100 | 可以指定但應受上限 | 僅影響已授權結果；保留上限 | 三個 Queue 使用，20 | 不應造成越權 |

不應再接受「外部 actor id 加上 status 就代表工作佇列」的設計。尤其：

- `supervisorId` 不應由前端決定 Manager 的案件範圍。
- `applicantId` 不應由前端決定 Applicant 的我的案件範圍。
- `CASHIER`、`PAYMENT_OPERATOR` 角色不能因為有權限就自動取得全部案件。

## 6. Applicant 讀取需求

最低已確認原則是 Applicant 至少可以查看自己建立的案件，包含：

- 自己的 DRAFT。
- 自己送出的 PENDING_MANAGER、PENDING_CASHIER。
- 自己的 APPROVED／UNPAID、APPROVED／PAID。
- 自己的 REJECTED_CLOSED。

案件進入下一個流程階段不應讓 Applicant 失去自己案件的 Detail 讀取權。建議未來 `MY_REQUESTS` scope 由 Backend 使用 `principal.userId` 強制查詢 `paymentRequest.applicant.id`，不接受外部 `applicantId` 作為授權依據。

目前尚未確認 Applicant 是否可以查看：

- 自己所屬 Department 其他人員案件。
- 自己被列為 Department Supervisor 的案件。
- 其他人代為建立或轉移的案件。

上述均標記 `UNCONFIRMED`，不能由「同部門」或「有登入」推論允許。

## 7. Supervisor Snapshot 讀取需求

已確認最低需求：`supervisorSnapshot.id == principal.userId` 的主管可以查看需要自己處理的 `PENDING_MANAGER` 案件。

目前文件與實作都以送出當下快照為業務關係，不應重新查目前 `DepartmentSupervisor` 取代既有快照。Read Authorization 也應使用 `PaymentRequest.supervisorSnapshot`，不應使用今天的主管設定。

尚未確認：主管完成核准或退回後，是否可以繼續查看該歷史案件。尤其需要區分：

- 主管核准後變成 PENDING_CASHIER。
- 後續變成 APPROVED／PAID。
- 主管退回後變成 REJECTED_CLOSED。

在業務決策確認前，不對這些狀態提供永久歷史查看承諾。

## 8. CASHIER 讀取需求

### 最小權限候選

- List：只允許 `PENDING_CASHIER`。
- Detail：只允許 `PENDING_CASHIER`，供出納核准／退回前查看。
- 需要 `CASHIER` authority。

優點是範圍小、容易驗收、資料暴露少；缺點是完成處理後無法在同一 Queue 查看歷史案件。

### 較寬鬆候選

- `PENDING_CASHIER`。
- `APPROVED`，供查看出納確認完成但尚未付款或已核准的案件。
- `REJECTED_CLOSED`，供查核歷史。
- 是否包含 `APPROVED／PAID`、是否只包含自己曾處理的案件，仍需決策。

較寬鬆版本方便稽核，但會擴大付款與客戶資料暴露面，也需要明確定義「曾由出納處理」是以 History actor、`approvedBy`，還是其他欄位判斷。本文件不替業務選定。

## 9. PAYMENT_OPERATOR 讀取需求

### 最小權限候選

- List：只允許 `APPROVED / UNPAID`。
- Detail：只允許付款登記前的 `APPROVED / UNPAID`。
- 需要 `PAYMENT_OPERATOR` authority。

優點是符合目前付款 Queue、最少暴露付款資料；缺點是付款完成後不能用同一範圍查核歷史。

### 較寬鬆候選

- `APPROVED / UNPAID`。
- `APPROVED / PAID`，供付款查核。
- 其他付款人登記的案件。
- 所有歷史付款案件，或只限自己曾登記的案件。

目前未確認 `PAYMENT_OPERATOR` 是否可以查看 `APPROVED / PAID`、其他付款人的案件或全部付款歷史；也未確認 `CASHIER` 是否可以自動使用 PAYMENT_OPERATOR 的讀取範圍。兩個 authority 不應互相推論。

## 10. 未確認的業務決策

以下項目必須由業務確認後才可實作：

1. 主管在 PENDING_MANAGER 以外狀態的歷史查看範圍。
2. CASHIER 是否可看 APPROVED、APPROVED／PAID、REJECTED_CLOSED。
3. CASHIER 是否只能看自己曾處理，或可看全部符合狀態的案件。
4. PAYMENT_OPERATOR 是否可看 APPROVED／PAID。
5. PAYMENT_OPERATOR 是否可看其他付款人登記的案件。
6. 是否要提供全部歷史案件查詢，以及其時間、部門、公司範圍。
7. Applicant 是否可看同部門案件或非自己建立的案件。
8. Department Supervisor 是否有部門全量查看權限。
9. 是否存在 ADMIN、會計師、經辦人或其他角色；目前 V4 沒有建立這些 Role。
10. 是否要把主管完成處理後的案件納入主管歷史 List。
11. Master Data 是否所有登入角色都可讀，或需限制為 Applicant／特定業務角色。
12. 報表查詢是否與工作佇列共用讀取範圍。

未確認規則一律使用 `UNCONFIRMED`，不以既有前端畫面、同部門、資料庫欄位名稱或角色名稱自行補完。

## 11. Detail 授權候選

登入者可以查看 Detail 的候選條件為以下任一項：

| 候選 | 條件 | 最小版本 | 較寬鬆版本 | 風險與影響 |
| --- | --- | --- | --- | --- |
| A Applicant | `paymentRequest.applicant.id == principal.userId` | 自己全部狀態 | 仍建議自己全部狀態，避免流程後失去追蹤 | 可能暴露自己案件的歷程與付款資訊，但符合業務追蹤需求 |
| B Supervisor Snapshot | `supervisorSnapshot.id == principal.userId` | 只有 PENDING_MANAGER | 加入已處理歷史案件 | 使用即時主管設定會錯誤授權；必須用快照 |
| C CASHIER | 有 `CASHIER` 且狀態屬出納範圍 | 只有 PENDING_CASHIER | PENDING_CASHIER、APPROVED、REJECTED_CLOSED 或「曾處理」 | 範圍擴大後可讀取付款與客戶資訊；「曾處理」需定義查詢來源 |
| D PAYMENT_OPERATOR | 有 `PAYMENT_OPERATOR` 且狀態屬付款範圍 | APPROVED／UNPAID | 再加入 APPROVED／PAID、其他付款人或全部付款歷史 | 付款資訊與財務資料暴露增加；不能因角色名稱自動加入 CASHIER 範圍 |

最小版本適合第一個可驗收實作；較寬鬆版本需先完成第 10 節業務決策。未決策前，未符合已確認關係的登入者應採 deny-by-default，但矩陣中仍以 `UNCONFIRMED` 表示業務範圍尚未定案。

## 12. 權限矩陣（設計草案）

下表是目前已確認最低需求與未決策項目的分離結果，不代表現有程式已套用。

| 使用者關係／權限 | DRAFT | PENDING_MANAGER | PENDING_CASHIER | APPROVED/UNPAID | APPROVED/PAID | REJECTED_CLOSED |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 案件 Applicant | ALLOW | ALLOW | ALLOW | ALLOW | ALLOW | ALLOW |
| `supervisorSnapshot` 本人 | UNCONFIRMED | ALLOW | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED |
| `CASHIER` | UNCONFIRMED | UNCONFIRMED | ALLOW | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED |
| `PAYMENT_OPERATOR` | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED | ALLOW | UNCONFIRMED | UNCONFIRMED |
| 其他已登入使用者 | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED | UNCONFIRMED |
| 未登入使用者 | DENY | DENY | DENY | DENY | DENY | DENY |

矩陣備註：

- Applicant 的 ALLOW 只代表 Applicant 等於該案件 `applicant_id`，不是所有登入者都可讀。
- Supervisor Snapshot 本人目前只有 PENDING_MANAGER 是明確最低需求；完成後歷史狀態需業務確認。
- CASHIER 與 PAYMENT_OPERATOR 的 `UNCONFIRMED` 不應在未確認前直接放寬；實作時應先以最小候選或明確 scope deny-by-default。
- List 與 Detail 必須使用同一個決策結果：List 不能因外部 filter 看見 Detail 不允許的案件，Detail 也不能繞過 List 的範圍。

## 13. List API 三種設計比較

### 方案 A：各用途獨立 Endpoint

例如：

```text
GET /api/payment-requests/mine
GET /api/payment-requests/manager-pending
GET /api/payment-requests/cashier-pending
GET /api/payment-requests/payment-pending
```

| 面向 | 評估 |
| --- | --- |
| 安全性 | 高；Endpoint 語意直接，Backend 可完全忽略 actor id |
| 可維護性 | 角色範圍清楚，但 Endpoint 數量會增加 |
| Frontend 改動 | 三個 Queue 需改 API function，Detail 仍需獨立授權 |
| Repository 複雜度 | 查詢方法可能增加；若抽共用 scope，仍可控制 |
| 報表擴充 | 報表可能再增加新的 Endpoint，容易產生重複 |
| 初階維護 | 直觀，錯誤範圍小；但需理解多個 Endpoint |

### 方案 B：單一 List Endpoint，Backend 依 Principal 限制

保留 `GET /api/payment-requests`，由 Backend 依登入者、authority 與固定用途決定範圍。

| 面向 | 評估 |
| --- | --- |
| 安全性 | 可高，但不能把外部 filter 當授權；所有 OR 規則集中後容易漏條件 |
| 可維護性 | Endpoint 少；多角色使用者的 Applicant OR Supervisor OR CASHIER OR PAYMENT_OPERATOR 條件會變複雜 |
| Frontend 改動 | 最少，但畫面需要知道後端如何依角色解讀相同 Query |
| Repository 複雜度 | 可能出現複雜 JPQL／Specification 與多個 OR 條件 |
| 報表擴充 | 彈性高，但容易把工作佇列、我的案件、報表查詢混在一起 |
| 初階維護 | 表面簡單，實際授權流程較難追蹤與測試 |

### 方案 C：單一 Query API 加明確 Scope

例如：

```text
GET /api/payment-requests?scope=MY_REQUESTS
GET /api/payment-requests?scope=MANAGER_PENDING
GET /api/payment-requests?scope=CASHIER_PENDING
GET /api/payment-requests?scope=PAYMENT_PENDING
```

| 面向 | 評估 |
| --- | --- |
| 安全性 | 高；每個 scope 可明確驗證 authority 與 Principal，user id 不由外部傳入 |
| 可維護性 | 比方案 B 清楚，scope 與規則一對一；需避免任意新增 scope |
| Frontend 改動 | Queue 只需加入明確 scope；可保留同一 API client |
| Repository 複雜度 | List Service 先解析 scope，再使用受控查詢；避免把全部 OR 混成一條 |
| 報表擴充 | 可新增報表專用 scope 或獨立報表 API，不必偽裝成工作佇列 |
| 初階維護 | scope 名稱可讀、測試案例容易對應；需維護 scope／authority 表 |

## 14. 主要建議方案

建議採用「方案 C 的明確 Scope + 共用 Detail Read Authorization Service」：

1. 第一階段只建立已確認的 `MY_REQUESTS`、`MANAGER_PENDING`、`CASHIER_PENDING`、`PAYMENT_PENDING` 概念，不先建立歷史、ADMIN 或報表 scope。
2. `MY_REQUESTS` 由 Backend 強制使用 Principal user id；不接受外部 `applicantId` 決定範圍。
3. `MANAGER_PENDING` 由 Backend 強制使用 Principal user id 對 `supervisorSnapshot.id`；不接受外部 `supervisorId`。
4. `CASHIER_PENDING` 必須有 `CASHIER`，狀態先限 `PENDING_CASHIER`。
5. `PAYMENT_PENDING` 必須有 `PAYMENT_OPERATOR`，狀態先限 `APPROVED / UNPAID`。
6. 未確認的歷史範圍不先加入查詢 scope。
7. 保留一般 filter 只能作為已授權 scope 內的縮小條件；不允許 filter 擴張 scope。
8. Detail 先判斷與已授權 scope 相同的關係與狀態，再回傳完整 Detail。

此方案不建立通用 ACL、Permission Framework 或 Workflow Engine，也不新增 Role。對單一工程師與初階維護者而言，scope 名稱能直接對應測試案例，且避免把多角色 OR 條件散落在 Controller、Vue 與 Repository。

## 15. 建議新增 Read Authorization Service

建議未來新增：

```text
tw.com.jsgcpa.paymentapproval.payment.service.PaymentRequestReadAuthorizationService
```

責任可以限制為：

- `canReadDetail(paymentRequestData, userId, authorities)`。
- `validateDetailAccess(...)`，不通過即回傳統一的 Not Found 或 Access Denied 策略，具體 HTTP 語意需另外確認。
- `resolveAllowedListScope(scope, userId, authorities)`。
- 集中定義 Applicant、Supervisor Snapshot、CASHIER、PAYMENT_OPERATOR 的已確認最低規則。

設計限制：

- 不直接呼叫 `SecurityContextHolder`。
- Controller 取得 `AuthenticatedUserPrincipal`，再傳入 `userId` 與 authorities 或明確 role flags。
- Service 接收純資料，例如 user id、authority set、案件 applicant id、snapshot id、status、payment status。
- 第一版政策判斷可保持純 Java，避免依賴 Repository；List Service 依解析結果傳入受控 Repository query。
- 若未來「曾處理歷史」需要查 History，應另加明確查詢與測試，不把它偷偷混入基本 scope。

List 與 Detail 的一致性方式：先以同一政策服務解析 scope；List 只查該 scope 的資料，Detail 查到案件後用同一政策服務驗證。兩者不得各自複製一套 if／role 判斷。

## 16. 建議實作順序

本階段不實作，建議後續依以下順序拆分：

### 階段 A：Detail 基本登入與 Applicant／主管 Snapshot 授權

- `GET /api/payment-requests/{id}` 要求登入。
- Applicant 只可讀自己的案件。
- Supervisor Snapshot 本人先只讀 PENDING_MANAGER。
- 其他關係 deny-by-default。

預計修改：SecurityConfig、PaymentRequestController、GetPaymentRequestDetailService、Read Authorization Service、Controller tests、Detail Service tests。

### 階段 B：Applicant 我的案件 List

- 建立 `MY_REQUESTS` scope。
- Backend 強制 applicant id 使用 Principal。
- 移除或忽略外部 `applicantId` 對授權的影響。

預計修改：PaymentRequestController、ListPaymentRequestsService、PaymentRequestRepository 查詢、paymentRequestApi、Applicant List View（若新增）。

### 階段 C：Manager Queue Backend 強制 Principal

- 建立 `MANAGER_PENDING` scope。
- Query 不再相信 `supervisorId`。
- Detail 與 List 共用 Supervisor Snapshot 政策。

預計修改：Manager Controller／List API、List Service、Repository query、Manager tests、Manager Vue API call。

### 階段 D：Cashier Queue

- 先採最小候選：`CASHIER` + `PENDING_CASHIER`。
- Cashier Detail 同步限制。

預計修改：SecurityConfig GET 規則、Cashier List／Detail Controller、Service、tests、Cashier Vue error handling。

### 階段 E：Payment Queue

- 先採最小候選：`PAYMENT_OPERATOR` + `APPROVED / UNPAID`。
- Payment Detail 同步限制。

預計修改：SecurityConfig GET 規則、Payment List／Detail Controller、Service、tests、Payment Vue error handling。

### 階段 F：歷史案件讀取範圍

- 先取得第 10 節業務決策，再決定 Cashier、Payment Operator、Supervisor 的歷史範圍。
- 若需要「曾處理」，再設計 History EXISTS 查詢；不先加入模糊全量查詢。

預計修改：Read Authorization Service、Repository 查詢、各角色 API／Vue、PostgreSQL E2E 與完整測試。

### 階段 G：Master Data GET 登入限制

- 先將公司、客戶、費用類型、價格 GET 改為 `authenticated()`。
- 暫不加入細粒度角色限制。

預計修改：SecurityConfig、Master Data controller tests、前端登入失效處理；不修改資料表。

## 17. 測試策略

### 17.1 Read Authorization Service 單元測試

至少涵蓋：

- Applicant 讀自己的每個狀態：ALLOW。
- Applicant 讀別人的案件：DENY。
- Supervisor Snapshot 本人讀 PENDING_MANAGER：ALLOW。
- 其他 user 讀 PENDING_MANAGER：DENY。
- CASHIER 讀 PENDING_CASHIER：ALLOW；無 CASHIER：DENY。
- PAYMENT_OPERATOR 讀 APPROVED／UNPAID：ALLOW；無 PAYMENT_OPERATOR：DENY。
- 外部 applicantId／supervisorId 與 Principal 不一致時，永遠不能變成授權依據。
- 未確認歷史 scope 不會被默認放寬。
- 多角色使用者只取得各自已確認 scope 的 OR 結果，不自動取得另一角色未確認歷史範圍。

### 17.2 Controller 測試

- 未登入 GET 回 401，且不呼叫 Application Service。
- List scope 與 Principal 傳遞正確。
- 修改 Query Parameter 不可改變傳入的受控 user id。
- Detail 403／404 策略固定且不洩漏案件存在性；具體選擇需先確認錯誤處理政策。

### 17.3 List／Detail Service 測試

- List 只查授權 scope。
- Detail 與 List 使用同一 Read Authorization policy。
- 列表和 Detail 的 status boundary 一致。
- 只讀流程不呼叫 save、delete 或任何寫入方法。
- Response 不因角色錯誤而多回傳未授權資料。

## 18. PostgreSQL E2E 策略

本階段不啟動 PostgreSQL E2E。未來每個 scope 應使用明確固定資料，至少準備：

- 兩個不同 Applicant 與各自案件。
- 一個 supervisor snapshot user，以及另一個目前即時主管設定，以驗證使用快照而非重新查主管。
- PENDING_MANAGER、PENDING_CASHIER、APPROVED／UNPAID、APPROVED／PAID、REJECTED_CLOSED 各狀態。
- CASHIER、PAYMENT_OPERATOR、無業務角色的登入帳號。
- List 以合法 Query Parameter 改成他人 id 時，結果不跨越 scope。
- 直接以已知 id 讀 Detail 時，與從 List 讀取的授權結果一致。
- 只查詢驗證，不以 SQL 直接 UPDATE 或 INSERT 修正測試結果。

E2E 每個 case 應記錄 HTTP status、error code、Response fields、DB 查詢結果與是否有讀取以外的 side effect。尚未確認的歷史範圍不要以 E2E 先替業務定案。

## 19. 前端改動影響

後續實作時，前端不應再以 `supervisorId=currentUser.userId` 或類似 actor id 宣告權限。建議：

- Queue API 使用明確 scope。
- UI 可保留角色提示與操作按鈕條件，但以 Backend 401／403 為最終結果。
- 401 導回登入；403 顯示無權限，不繼續嘗試讀取或操作。
- Detail 連結可被使用者手動輸入，因此必須依賴 Backend Detail 授權。
- List、Detail、action route 的前端判斷不能取代 Backend 授權。
- Master Data GET 若改為 authenticated，建立草稿頁現有登入失效處理需確認完整。

## 20. 預計修改檔案

以下是未來實作的預計範圍，不是本階段已修改檔案：

| 階段 | 預計檔案 |
| --- | --- |
| A | `backend/src/main/java/.../security/config/SecurityConfig.java`、`payment/controller/PaymentRequestController.java`、`payment/service/GetPaymentRequestDetailService.java`、新增 `PaymentRequestReadAuthorizationService.java`、對應 Controller／Service tests |
| B | List Controller／Service／Repository、Payment API type、Applicant List View、tests |
| C | Manager List／Detail backend、Repository query、Manager View／API、tests |
| D | Cashier GET Security／Controller／Service、Cashier View、tests |
| E | Payment GET Security／Controller／Service、Payment View、tests |
| F | History query、Read Authorization、各角色 E2E 與 tests |
| G | SecurityConfig、Master Data controller tests、必要的前端 error handling |

本階段實際新增檔案只有本文件：`docs/payment-read-authorization-design.md`。

## 21. 尚未處理範圍

- 尚未修改 SecurityConfig 或任何正式授權程式。
- 尚未開始 List Read Authorization。
- 尚未開始 Detail Read Authorization。
- 尚未修改 List Query Contract 或移除 `applicantId`／`supervisorId`。
- 尚未新增 `PaymentRequestReadAuthorizationService`。
- 尚未決定歷史案件、部門互閱、跨公司、CASHIER／PAYMENT_OPERATOR 交集等規則。
- 尚未新增 ADMIN、會計師、經辦人或其他 Role。
- 尚未建立 ACL table、Permission Framework 或通用 Workflow Engine。
- 尚未修改 V1～V4，未建立 V5。
- 尚未執行 Maven、npm build、Spring Boot startup 或 PostgreSQL E2E；本階段只做文件、路徑、API、欄位與 Git 狀態核對。

