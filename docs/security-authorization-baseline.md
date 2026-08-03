# 請款簽核系統 Authentication／Authorization 安全基線

## 1. 文件目的與適用範圍

本文件整理目前請款簽核系統的 Authentication、Authorization、Session、CSRF、Principal、HTTP 錯誤語意，以及前後端安全邊界。內容以目前 Java、Vue、V4 Migration、既有測試與 PostgreSQL／瀏覽器驗收結果為準。

本階段只整理文件，不修改正式 Java、Vue、SecurityConfig、Controller、Service、Repository、Entity、DTO、Migration、Database 或 API Contract。

盤點日期：2026-08-03

目前已完成的安全範圍：

- Username／Password 登入
- Spring Security Session Authentication
- HttpSession 與 `JSESSIONID` Cookie
- PasswordEncoder
- DatabaseUserDetailsService
- `AuthenticatedUserPrincipal`
- Session fixation protection
- CSRF Token
- `CASHIER`、`PAYMENT_OPERATOR` authorities
- Business API 的基本 Authentication／Authorization
- Payment List Scope
- Payment Detail Read Authorization
- Master Data GET Authentication

目前刻意尚未處理：

- MANAGER_HISTORY、CASHIER_HISTORY、PAYMENT_HISTORY
- ADMIN authority 與 ADMIN API
- ACL／細緻的資料列權限模型
- Master Data 寫入權限
- 其他尚未定義的跨部門查詢規則

## 2. Authentication 基線

### 2.1 登入模型

系統使用 Backend Username／Password 搭配 Spring Security Session，不使用 JWT。

| 項目 | 目前規則 |
| --- | --- |
| 登入 API | `POST /api/auth/login` |
| 憑證 | `username`、`password` |
| Session | `HttpSession` |
| Cookie | `JSESSIONID`，由伺服器管理，前端只使用 `withCredentials` |
| JWT | 不使用 |
| Password | 只交給 Backend 驗證，不進入業務 Request／Response |
| 登入使用者來源 | `app_users`、`app_user_credentials`、`app_user_roles` |
| 啟用狀態 | `AppUser.active` 為 false 時不可登入 |
| 最後登入時間 | 成功登入後更新 `lastLoginAt` |
| Session fixation | 使用 `ChangeSessionIdAuthenticationStrategy` |

### 2.2 Authentication API

| API | Authentication | CSRF | 說明 |
| --- | --- | --- | --- |
| `POST /api/auth/login` | `permitAll` | 需要 | 用登入前取得的 CSRF Token 建立 Session |
| `GET /api/auth/csrf` | `permitAll` | 不需要 | 取得 CSRF Token |
| `GET /api/auth/me` | authenticated | 不需要 | 回傳目前登入者 |
| `POST /api/auth/logout` | authenticated | 需要 | 清除 Session Authentication |

`/api/auth/me` 是前端重新載入頁面時確認 Session 是否仍有效的依據。前端不把 Session ID 或登入狀態放入 `localStorage`。

### 2.3 AuthenticatedUserPrincipal

Backend Authentication Principal 包含：

- `userId`
- `username`
- `displayName`
- `enabled`
- `GrantedAuthority` 集合

業務 Controller 以 Principal 的 `userId` 作為目前登入者識別，不以前端傳入的 actor ID 作為授權依據。

## 3. Authority 基線

目前資料庫與 Java Enum 只定義以下兩個 authority：

| Authority | 目前用途 |
| --- | --- |
| `CASHIER` | 出納待辦、出納核准／退回、出納流程讀取 |
| `PAYMENT_OPERATOR` | 付款待辦、付款登記、付款流程讀取 |

目前不使用 `ROLE_` prefix，也不使用 `hasRole`；需要特定權限時使用 `hasAuthority`。

`APPLICANT` 與 `MANAGER` 目前不是 Security Role。申請人與主管權限以登入者 `userId` 搭配案件資料判斷：

- 申請人：`paymentRequest.applicant.id`
- 主管：`paymentRequest.supervisorSnapshot.id`

主管授權使用請款單送出當下的 `supervisorSnapshot`，不重新查詢目前的 `DepartmentSupervisor` 取代歷史快照。

## 4. API Authentication／Authorization 矩陣

| API | Authentication | Authorization | Actor 來源 | CSRF |
| --- | --- | --- | --- | --- |
| `POST /api/payment-requests/drafts` | authenticated | Service 驗證申請人與主檔資料 | Principal `userId` | 需要 |
| `POST /api/payment-requests/{id}/submit` | authenticated | Service 驗證案件及申請人 | Principal `userId` | 需要 |
| `POST /api/payment-requests/{id}/manager-approve` | authenticated | Service 驗證 `supervisorSnapshot.id` | Principal `userId` | 需要 |
| `POST /api/payment-requests/{id}/manager-reject` | authenticated | Service 驗證 `supervisorSnapshot.id` | Principal `userId` | 需要 |
| `POST /api/payment-requests/{id}/cashier-approve` | `hasAuthority("CASHIER")` | Service 驗證案件狀態與出納資料 | Principal `userId` | 需要 |
| `POST /api/payment-requests/{id}/cashier-reject` | `hasAuthority("CASHIER")` | Service 驗證案件狀態與出納資料 | Principal `userId` | 需要 |
| `POST /api/payment-requests/{id}/record-payment` | `hasAuthority("PAYMENT_OPERATOR")` | Service 驗證案件為 `APPROVED／UNPAID` | Principal `userId` | 需要 |
| `GET /api/payment-requests` | authenticated | 必須指定合法 List Scope | Principal `userId` | 不需要 |
| `GET /api/payment-requests/{id}` | authenticated | Detail Read Authorization | Principal `userId` 與 authorities | 不需要 |
| `GET /api/master/companies` | authenticated | 目前 role-neutral | Session Principal | 不需要 |
| `GET /api/master/customers` | authenticated | 目前 role-neutral | Session Principal | 不需要 |
| `GET /api/master/expense-types` | authenticated | 目前 role-neutral | Session Principal | 不需要 |
| `GET /api/master/expense-types/{id}/prices` | authenticated | 目前 role-neutral | Session Principal | 不需要 |

所有 unsafe method 均由 CSRF filter 保護。Business Service 仍須執行案件狀態、資料關係、版本與業務規則檢查；SecurityConfig 的 authenticated 或 authority matcher 不取代 Service 授權。

## 5. CSRF 基線

### 5.1 Backend

- CSRF 使用 `HttpSessionCsrfTokenRepository`。
- Header name：`X-CSRF-TOKEN`。
- Parameter name：`_csrf`。
- Token 由 `GET /api/auth/csrf` 取得。
- GET 屬於 safe method，不需 CSRF。
- POST、PUT、PATCH、DELETE 屬於 unsafe method，必須提供有效 CSRF Token。
- 無 Token 或 Token 錯誤時回傳 HTTP 403、`INVALID_CSRF_TOKEN`。

### 5.2 Frontend

- Axios client 使用 `withCredentials: true`。
- unsafe request 送出前自動取得 memory-only CSRF Token。
- CSRF Token 不寫入 `localStorage` 或 `sessionStorage`。
- 收到 `INVALID_CSRF_TOKEN` 時清除記憶體 Token，通知 UI 重新取得 Token。
- 收到未預期的 HTTP 401 時清除 Auth Store 並導向登入頁。

## 6. Payment List Scope

`GET /api/payment-requests` 不接受未指定 scope 的 Legacy 查詢；缺少 scope 時回傳：

- HTTP 400
- `PAYMENT_REQUEST_LIST_SCOPE_REQUIRED`

### 6.1 Scope 規則

| Scope | 必要 authority | Backend 強制範圍 | 禁止由前端取代的欄位 |
| --- | --- | --- | --- |
| `MY_REQUESTS` | authenticated | `applicantId = Principal.userId` | 不接受外部 `applicantId` 作為本人範圍 |
| `MANAGER_PENDING` | authenticated | `supervisorSnapshotId = Principal.userId`、`approvalStatus = PENDING_MANAGER` | 不接受外部 `supervisorId` |
| `CASHIER_PENDING` | `CASHIER` | `approvalStatus = PENDING_CASHIER` | 不可用任意 status 取代 scope |
| `PAYMENT_PENDING` | `PAYMENT_OPERATOR` | `approvalStatus = APPROVED`、`paymentStatus = UNPAID` | 不可用任意 payment status 取代 scope |

Scope 與 query filter 衝突時回傳：

- HTTP 400
- `PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT`

Scope authority 不足時回傳：

- HTTP 403
- `PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN`

## 7. Payment Detail Read Authorization

Detail API 未授權或案件不存在時，統一回傳：

- HTTP 404
- `PAYMENT_REQUEST_NOT_FOUND`

這避免向無權限使用者洩漏案件是否存在。

目前 Detail 的允許條件為以下任一項：

| 登入者關係／authority | 狀態條件 | 結果 |
| --- | --- | --- |
| Applicant | `paymentRequest.applicant.id = Principal.userId` | Allow，包含本人案件目前各流程狀態 |
| Supervisor Snapshot | `supervisorSnapshot.id = Principal.userId` 且 `approvalStatus = PENDING_MANAGER` | Allow |
| `CASHIER` | `approvalStatus = PENDING_CASHIER` | Allow |
| `PAYMENT_OPERATOR` | `approvalStatus = APPROVED` 且 `paymentStatus = UNPAID` | Allow |
| 其他情況 | 不符合上述任一條件 | 以 `PAYMENT_REQUEST_NOT_FOUND` 拒絕 |

Detail 授權使用 Backend 查出的案件資料，不信任前端角色、前端 status、URL 類型或前端顯示按鈕。

## 8. HTTP Status 與 Error Code 基線

| HTTP Status | Error Code | 使用情境 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Request DTO 驗證失敗 |
| 400 | `INVALID_REQUEST_BODY` | Request body 缺少或格式錯誤 |
| 400 | `INVALID_QUERY_PARAMETER` | Query parameter 型別錯誤 |
| 400 | `PAYMENT_REQUEST_LIST_SCOPE_REQUIRED` | Payment List 未指定 scope |
| 400 | `PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT` | Scope 與外部 filter 衝突 |
| 401 | `UNAUTHENTICATED` | 未登入呼叫需登入 API |
| 401 | `INVALID_CREDENTIALS` | 登入帳密錯誤 |
| 403 | `ACCESS_DENIED` | 已登入但沒有必要 authority |
| 403 | `INVALID_CSRF_TOKEN` | Unsafe method 的 CSRF Token 無效或缺少 |
| 403 | `PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN` | List Scope authority 不足 |
| 404 | `PAYMENT_REQUEST_NOT_FOUND` | 案件不存在或 Detail 無讀取權限 |

錯誤 Response 使用既有 `ApiErrorResponse`，包含 timestamp、status、error、code、message、path 與 fieldErrors。安全錯誤時間使用 `Asia/Taipei`。

## 9. Frontend 安全邊界

| 前端元件 | 安全責任 |
| --- | --- |
| Auth Store | Login、`/api/auth/me`、Logout、Session 狀態與清除登入狀態 |
| `http.ts` | `withCredentials`、unsafe method CSRF header、401／403 事件 |
| `csrfApi.ts`／`csrfToken.ts` | 取得與 memory-only 保存 CSRF Token |
| `router/index.ts` | 需要登入的頁面導向 Login，保存安全的 redirect path |
| Payment API | 只傳業務欄位，不傳 actor ID 作為授權依據 |
| List views | 依 scope 顯示 UI，不取代 Backend scope enforcement |
| Detail view | 顯示操作按鈕的條件只屬 UX；真正權限由 Backend 驗證 |

Frontend role 判斷不得被視為安全邊界。任何人都可能直接呼叫 API，因此所有重要授權必須在 Backend SecurityConfig 或 Service 再次驗證。

## 10. 已完成驗證證據

### 10.1 Backend

- `mvnw.cmd clean test`：BUILD SUCCESS
- 420 tests
- 0 failures
- 0 errors
- `mvnw.cmd clean compile`：BUILD SUCCESS
- Java release 21 編譯成功
- Flyway V1～V4 validation successful
- Schema version：4
- EntityManagerFactory 初始化成功
- Repository beans 正常建立

### 10.2 Frontend

- `npm.cmd run build` 成功
- TypeScript 0 errors
- 只有既有 Rollup pure-comment 與 chunk size warning，沒有編譯錯誤

### 10.3 PostgreSQL Read-only E2E

Master Data 四個 GET 已驗證：

- 未登入：全部 HTTP 401
- `e2e.applicant`：四個端點全部 HTTP 200，角色為空仍可讀取
- `e2e.cashier`：四個端點全部 HTTP 200
- GET 未要求 CSRF

驗證前後資料筆數一致：

| Table | Before | After |
| --- | ---: | ---: |
| `companies` | 1 | 1 |
| `customers` | 1 | 1 |
| `expense_types` | 3 | 3 |
| `expense_price_settings` | 2 | 2 |
| `payment_requests` | 22 | 22 |
| `payment_request_items` | 40 | 40 |
| `approval_histories` | 44 | 44 |
| `payment_request_attachments` | 0 | 0 |

本次驗證沒有使用 SQL UPDATE／INSERT，也沒有建立 V5 或修改資料庫。

### 10.4 Browser E2E

- Applicant 登入成功。
- 開啟新增請款草稿頁面成功。
- 公司、客戶、費用類型下拉資料成功載入。
- 未執行建立草稿或其他業務寫入。
- 登出後回到登入頁。

## 11. 目前安全風險與後續工作

目前 `SecurityConfig` 對未明確列出的 URL 仍使用 `.anyRequest().permitAll()`。因此新增 API 必須在上線前明確加入 matcher；後續可評估 deny-by-default，但需先盤點健康檢查、靜態資源與所有既有公開路徑，避免直接改變既有契約。

後續工作順序建議：

1. 驗證 MANAGER_HISTORY、CASHIER_HISTORY、PAYMENT_HISTORY 的讀取授權。
2. 設計 ADMIN authority 與 ADMIN API 授權矩陣。
3. 對 Master Data 寫入 API 建立明確的 authority 與 audit 規則。
4. 評估 ACL 或跨部門查詢需求；未有實際需求前不建立通用 ACL 引擎。
5. 讓新增 API 在 SecurityConfig 採明確 allowlist，並補上匿名、無權限、合法 authority、CSRF 與 PostgreSQL E2E 測試。

## 12. 交付檢查表

- [x] API Authentication 已盤點
- [x] Actor 使用 Backend Principal 已盤點
- [x] Request actor ID 不作為授權來源已盤點
- [x] Session Authentication 已盤點
- [x] CSRF 已盤點
- [x] Authorities 已盤點
- [x] Payment write API 已盤點
- [x] Payment List Scope 已盤點
- [x] Payment Detail Read Authorization 已盤點
- [x] Master Data GET Authentication 已盤點
- [x] Frontend 401／403 行為已盤點
- [x] HTTP Status／Error Code 已盤點
- [x] Maven test／compile 已驗證
- [x] Frontend build 已驗證
- [x] PostgreSQL read-only E2E 已驗證
- [x] 未修改正式程式碼與資料庫
- [ ] MANAGER_HISTORY／CASHIER_HISTORY／PAYMENT_HISTORY
- [ ] ADMIN
- [ ] ACL
- [ ] Master Data write authorization
