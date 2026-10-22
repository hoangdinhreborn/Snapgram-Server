## 2026-10-21 - Fix search user thieu trang thai Blue Tick
- `auth-service/.../service/UserService.java:94` — map verified/verifiedUntil trong searchUsers | Vi sao: search tra false lam badge khong hien o Explore

## 2026-10-21 - Expose Blue Tick trong ho so user cho FE
- `auth-service/.../dto/UserProfileResponse.java:20` — them verified/verifiedUntil | Vi sao: FE can hien thi badge va han su dung
- `auth-service/.../dto/UserMeResponse.java:20` — them verified/verifiedUntil | Vi sao: auth context FE biet trang thai tick hien tai
- `auth-service/.../service/UserService.java:319` — them helper tick effective + map full/public profile | Vi sao: badge het han theo verified_until thay vi is_verified
- `auth-service/.../service/AuthService.java:498` — map tick effective trong /api/auth/me | Vi sao: dong bo trang thai tick voi profile

## 2026-10-21 - Tu dong reconcile MoMo khi IPN that lac
- `payment/.../config/PaymentReconcileScheduler.java:30` — them scheduler quet MOMO PENDING/EXPIRED va goi `PaymentService.reconcile` theo batch | Vi sao: MoMo public partner khong gui IPN cho `payWithATM`, don da thanh toan van ket o PENDING
- `payment/.../config/PaymentReconcileProperties.java:12` — them config provider/min-age/expired-grace/batch | Vi sao: cho phep chinh tan suat va thoi gian cho giao dich den tre ma khong sua code
- `payment/.../repository/PaymentOrderRepository.java:44` — them query `findReconcileCandidates` (PENDING cu + EXPIRED trong grace) | Vi sao: chi quet PENDING se bo sot don vua bi expiry truoc khi kip reconcile
- `payment-service/src/main/resources/application.yml:59` — them block `payment.reconcile` bat mac dinh | Vi sao: scheduler tu chay sau restart, khong can thao tac admin cho tung don
- `payment-service/.../PaymentReconcileSchedulerTest.java:23` — them 3 test cho reconcile batch/loi tung don/khong co ung vien | Vi sao: khoa hanh vi scheduler va tranh hoi quy khi sua query

## 2026-10-20 - Xoa dead config VNPAY_IPN_URL (IPN chi cau hinh tren portal)
- `payment/.../adapter/vnpay/VnpayProperties.java:13` — bo field `ipnUrl` | Vi sao: VNPay khong nhan IPN URL trong payment URL, URL IPN gan voi Terminal tren merchant portal
- `payment-service/src/main/resources/application.yml:35` — bo `ipn-url` | Vi sao: map toi property khong con dung, tranh gay hieu nham la doi env se doi duoc IPN
- `.env:30`, `.env.example:40` — bo bien, thay bang comment portal | Vi sao: sua env khong lam VNPay goi IPN sang URL moi
- `payment/.../config/PaymentGatewayStartupLogger.java:37` — bo cum "nhu VNPAY_IPN_URL" | Vi sao: chi MoMo moi dung `ipnUrl` trong request, cau cu gay hieu nham
- `docs/services/payment/VNPAY_E2E_TEST.md:267` — Test C chi sua portal, them buoc kiem tra tunnel tra `RspCode=97` | Vi sao: quy trinh dung thuc te, tranh sua env vo ich

## 2026-10-20 - E2E VNPay sandbox + fix auth Kafka consumer khong chay
- `auth-service/.../config/KafkaConfig.java:28` — them `@EnableKafka` + `ConsumerFactory`/`ConcurrentKafkaListenerContainerFactory` | Vi sao: Boot 4 tach Kafka auto-config sang starter rieng, thieu factory nen `@KafkaListener` bi bo qua, Blue Tick khong bao gio duoc cap
- `docs/services/payment/VNPAY_E2E_TEST.md:1` — huong dan tu test 3 kieu (thanh toan that, IPN gia lap, tunnel) + checklist/loi thuong gap | Vi sao: chot quy trinh E2E da chay that va de nguoi khac tai lap
- `README.md:418` — them link tai lieu E2E VNPay | Vi sao: de tim thay huong dan

## 2026-10-20 - Don code thua payment-service + fix thieu bean PaymentStateMachine
- `payment/.../service/PaymentStateMachine.java:7` — them @Component | Vi sao: PaymentService inject bean nay nhung truoc do khong co annotation/bean -> app khong boot duoc
- `payment/.../dto/PaymentUrl.java` — xoa, gateway.createPayment tra String | Vi sao: record chi dung 1 field url, expireDate khong noi nao doc
- `payment/.../port/PaymentGateway.java:24` — bo paymentId khoi CreatePaymentCommand, amount khoi QueryCommand | Vi sao: ca 2 field khong adapter nao doc
- `payment/.../dto/PaymentCallbackResult.java:5` — bo tmnCode | Vi sao: khong consumer nao doc, merchant code da check trong VnpayPaymentGateway
- `payment/.../port/ProductPrice.java:3` — bo duration | Vi sao: catalog khai bao 30 ngay nhung khong noi nao dung
- `payment/.../repository/*.java` — xoa 3 method derived khong dung (existsByUserIdAndOrderId, countByUserIdAndProductCodeAndStatus, existsByEventId) | Vi sao: khong co caller, Spring Data van tao query thua
- `payment/.../port/PaymentGatewayRegistry.java:31 + config/PaymentGatewayStartupLogger.java:27` — bo providers(), dung vnpay.isConfigured() | Vi sao: providers chi test dung; helper configured trung logic isConfigured
- `payment/.../service/PaymentService.java:208 + adapter/vnpay/VnpayPaymentGateway.java:21` — bo re-fetch sau markSuccess, gop 2 DateTimeFormatter trung nhau | Vi sao: re-fetch vo nghia trong cung persistence context; 2 formatter y het

## 2026-10-20 - VNPay: khoa success theo 2 ma + verify QueryDR + guard config + 16 test
- `payment/.../dto/PaymentCallbackResult.java:11 + adapter/vnpay/VnpayPaymentGateway.java:127` — them transactionStatus, success chi khi ca vnp_ResponseCode va vnp_TransactionStatus = 00 | Vi sao: truoc do chi check ResponseCode nen giao dich status 02 van bi coi la thanh cong va cap Blue Tick sai
- `payment/.../adapter/momo/MomoPaymentGateway.java:151` — truyen transactionStatus = null | Vi sao: MoMo khong co ma nay, giu nguyen hanh vi cu
- `payment/.../adapter/vnpay/VnpayProperties.java:22 + VnpayPaymentGateway.java:54` — isConfigured() + guard truoc khi tao URL | Vi sao: thieu tmnCode/hashSecret truoc do van tao don PENDING va tra URL sai chu ky
- `payment/.../adapter/vnpay/VnpaySigner.java:54` — signable() dung chung cho callback; queryDrResponseRaw/verifyQueryDrResponse theo thu tu pipe co dinh | Vi sao: response QueryDR ky bang raw '|' khac callback (sort + URLEncode), verify sai format se luon fail
- `payment/.../adapter/vnpay/VnpayQueryClient.java:35` — timeout 10s/30s + verify vnp_SecureHash cua response | Vi sao: RestClient mac dinh khong timeout gay treo request admin; response khong verify thi khong tin duoc du lieu doi soat
- `payment/.../adapter/vnpay/VnpayPaymentGateway.java:111` — bat buoc du vnp_ResponseCode + vnp_TransactionStatus truoc khi ket luan | Vi sao: thieu field ma van mac dinh responseCode 00 se grant sai
- `payment-service/src/test/.../VnpayPaymentUnitTest.java:1` — 16 test: create URL/amount x100/chu ky, verify 8 case, QueryDR 4 case, ack 5 ma | Vi sao: truoc do VNPay chi co 3 test signer/return, khong khoa duoc hanh vi adapter
- `payment-service/src/test/.../PaymentUnitTest.java:95` — cap nhat constructor PaymentCallbackResult | Vi sao: record them field transactionStatus

## 2026-10-19 - Sua 3 rui ro mat tien/mat event sau review MoMo + them transactional outbox
- `docs/services/payment/MOMO_PAYMENT_FLOW.md:1` — tai lieu so do luong MoMo (7 so do Mermaid) + ket qua review | Vi sao: chot lai luong va ghi ro 3 rui ro truoc khi sua
- `README.md:417` — them link tai lieu vao bang tham khao | Vi sao: de nguoi khac tim thay docs moi
- `payment/.../service/PaymentStateMachine.java:21` — cho phep EXPIRED/FAILED/CANCELLED -> SUCCESS | Vi sao: IPN success den tre hoac khach tra o lan thu sau, da thu tien thi phai cap quyen
- `payment/.../service/PaymentService.java:107` — IPN success luon markSuccess ke ca don EXPIRED/FAILED + log WARN; IPN fail ngoai PENDING thi giu nguyen trang thai | Vi sao: truoc do ack 0 nhung khong doi trang thai = khach mat tien khong nhan duoc quyen
- `payment/.../repository/PaymentEventLogRepository.java:19` — INSERT ON CONFLICT (event_id) DO NOTHING tra update count | Vi sao: bat exception tu saveAndFlush lam Hibernate session rollback-only, return false khong cuu duoc transaction
- `payment-service/src/main/resources/db/migration/V3__add_payment_outbox.sql:1 + entity/PaymentOutbox.java + repository/PaymentOutboxRepository.java` — bang outbox 1 row/don | Vi sao: event payment.completed phai ghi cung transaction voi don hang de khong lech trang thai
- `payment/.../service/PaymentOutboxDispatcher.java:1 + PaymentOutboxStateService.java:1` — dispatcher poll 2s, lock+lease, backoff, qua max-attempts -> FAILED | Vi sao: Kafka loi thi gui lai thay vi mat event cap Blue Tick
- `payment/.../port/PaymentEventPublisher.java:12 + adapter/kafka/KafkaPaymentEventPublisher.java:23` — tra CompletableFuture de biet broker ACK/loi | Vi sao: dispatcher can biet ket qua that moi dam markSent
- `payment-service/src/main/resources/application.yml:60` — cau hinh payment.outbox.* | Vi sao: tune poll/batch/lease/attempts bang env khi van hanh
- `test PaymentUnitTest.java:64 + PaymentOutboxDispatcherTest.java:1` — test transition moi, IPN EXPIRED/FAILED success, idempotent event log, dispatcher retry | Vi sao: khoa hanh vi cua 3 fix, tranh tai phat loi

## 2026-10-17 - Test MoMo voi sandbox key that + fix resultCode 1000/1005
- `.env:33` — dien MOMO_PARTNER_CODE/ACCESS_KEY/SECRET_KEY (sandbox key chinh thuc MOMOLRJZ20181206) | Vi sao: truoc do de trong, khong the goi API that
- `payment/.../adapter/momo/MomoPaymentGateway.java:173` — found = success || transId>0 || resultCode==1000 | Vi sao: sandbox tra 1000/transId=0 cho don pending, truoc do reconcile hoi 404 khong ton tai
- `payment/.../test/MomoSandboxLiveTest.java:25` — 4 test live env-gated: create/query pending/query khong ton tai/verifyCallback | Vi sao: chay dung code Java goi sandbox, tu skip khi khong co env de test offline khong loi mang
- `payment/.../test/MomoPaymentUnitTest.java:146` — case resultCode 1000 pending + 1005 khong ton tai | Vi sao: day la hanh vi that lay tu sandbox phai khoa khi map QueryResult

## 2026-10-17 - Hoan thien luong thanh toan: consumer payment.completed (cap Blue Tick) + trang thai cau hinh
- `auth/.../service/PaymentCompletedConsumer.java:27` — @KafkaListener payment.completed, idempotency bang auth_payment_processed_events, cong thuc GREATEST(now, verified_until)+duration | Vi sao: V6 + entity idempotency da co san nhung khong ai consume nen thanh toan khong cap quyen
- `auth/.../dto/PaymentCompletedEvent.java:10 + config/PaymentGrantProperties.java:19` — DTO roi rieng + map productCode->Duration (payment.grants.BLUE_TICK=30d) | Vi sao: auth rieng module khong import duoc DTO payment, thoi han phai cau hinh duoc qua env
- `auth-service/src/main/resources/application.yml:41 + 58` — kafka consumer (String deserializer, earliest, group auth-service) + payment.grants mac dinh | Vi sao: khong cau hinh consumer thi @KafkaListener khong doc duoc JSON
- `auth/.../config/KafkaConfig.java:49` — DefaultErrorHandler + DeadLetterPublishingRecoverer vao <topic>.DLT sau retry | Vi sao: payment.completed.DLT da tao trong create-topics.sh, loi DB khong duoc bo mat quyen
- `payment/.../config/PaymentGatewayStartupLogger.java:20` — log VNPay/MoMo da cau hinh chua + canh bao IPN localhost khi boot | Vi sao: thieu key se thay canh bao ngay lap, khong doan loi 400
- `payment/.../port/PaymentGatewayRegistry.java:31 + test MomoPaymentUnitTest.java:174` — providers() + test thieu key create loi ro rang | Vi sao: logger can danh sach provider, MoMo chua config phai loi truoc khi goi API
- `.env:32 + .env.example:50 + infra/docker-compose.yml:498` — block MOMO_* (3 key trong, URL tunnel san) + comment env_file | Vi sao: user chi can dien 3 key MoMo la chay duoc
- `test PaymentCompletedConsumerTest.java:25 + PaymentGrantPropertiesTest.java:13` — 6 case grant/idempotency/skip + test bind "30d" | Vi sao: chan loi cong don thoi han, retry trung eventId, dinh dang duration

## 2026-10-17 - Tich hop MoMo Payment API v2 vao payment-service (ben canh VNPay)
- `payment/.../adapter/momo/MomoProperties.java:9 + MomoSigner.java:14 + MomoPaymentGateway.java:26 + MomoQueryClient.java:19` — adapter MoMo create/IPN/query | Vi sao: can gateway MoMo dung port PaymentGateway
- `payment/.../port/PaymentGateway.java:13 + PaymentGatewayRegistry.java:13` — them provider()/queryTransaction/ipnAck + registry chon gateway | Vi sao: 2 provider khong the gang 1 bean
- `payment/.../service/PaymentService.java:50 + controller/PaymentController.java:37 + MomoCallbackController.java:33 + VnpayCallbackController.java:35` — them endpoint MoMo va truyen provider | Vi sao: tach theo provider, khong sua flow VNPay
- `payment/.../entity/PaymentOrder.java:46 + db/migration/V2__rename_payment_provider_columns.sql:2` — doi cot vnpay_* thanh provider_* | Vi sao: cot phai dung nghia khi luu ca MoMo
- `payment-service/src/main/resources/application.yml:43 + .env.example:44` — cau hinh MOMO_* (sandbox) | Vi sao: partnerCode/accessKey/secretKey lay tu env
- `api-gateway/.../JwtAuthenticationFilter.java:62` — public prefix momo/ipn + momo/return | Vi sao: MoMo goi truc tiep khong co JWT
- `payment-service/src/test/.../MomoPaymentUnitTest.java:68 + PaymentUnitTest.java:94` — test chu ky theo vector docs MoMo + sua constructor | Vi sao: khoa thu tu field va doi so provider

## 2026-10-16 - Hoan thien payment-service VNPay: expire scheduler + admin API + return redirect
- `payment/.../PaymentServiceApplication.java` — bat @EnableScheduling | Vi sao: scheduler expire can chay nen
- `payment/.../PaymentOrderRepository.java` — them bulk expire + Specification | Vi sao: chong race IPN, loc admin
- `payment/.../PaymentService.java` — them expire + listAll | Vi sao: don qua han phai EXPIRED
- `payment/.../PaymentExpiryScheduler.java` — quet PENDING qua han 1p | Vi sao: tranh don treo mai
- `payment/.../AdminPaymentController.java` — GET /api/admin/payments | Vi sao: admin can soat don
- `payment/.../UserContext.java + AccessDeniedException.java + GlobalExceptionHandler.java` — them requireAdmin/403 | Vi sao: chan user thuong vao admin
- `payment/.../VnpayCallbackController.java + application.yml + .env.example` — return 302 ve frontend | Vi sao: frontend can URL ket qua
- `payment/.../PaymentUnitTest.java` — them 3 test expire/admin/redirect | Vi sao: khoa hanh vi moi

## 2026-10-16 - Them QueryDR reconcile + sua bug compose/objectmapper
- `payment/.../VnpayQueryClient.java` — hoi thang VNPay xac minh GD | Vi sao: IPN that lac can doi chieu
- `payment/.../PaymentService.java` — tach markSuccess + reconcile | Vi sao: tranh duplicate publish event
- `payment/.../AdminPaymentController.java` — POST reconcile chi admin | Vi sao: cuu don mat IPN
- `payment/.../KafkaConfig.java` — tu tao ObjectMapper bean | Vi sao: Boot 4 het auto-config Jackson2
- `infra/docker-compose.yml` — xoa VNPAY ${...} thua | Vi sao: noi suy rong de len env_file
