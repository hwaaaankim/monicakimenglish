(() => {
    'use strict';
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    async function post(url, data) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 45000);
        try {
            const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
            if (token && csrfHeader) headers[csrfHeader] = token;
            const response = await fetch(url, { method: 'POST', credentials: 'same-origin', headers,
                body: JSON.stringify(data), signal: controller.signal });
            if (response.redirected) throw new Error('로그인이 만료되었습니다. 로그인한 뒤 같은 주문의 결제 상태를 다시 확인해 주세요.');
            const result = await response.json().catch(() => ({}));
            if (!response.ok) {
                if (result.redirectUrl) { location.assign(result.redirectUrl); return null; }
                throw new Error(result.message || (response.status === 403 ? '로그인 상태를 확인하고 다시 시도해 주세요.' : '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'));
            }
            return result;
        } catch (error) {
            if (error.name === 'AbortError') throw new Error('결제 결과 확인이 지연되고 있습니다. 같은 주문으로 다시 확인해 주세요.');
            throw error;
        } finally { clearTimeout(timer); }
    }
    function showError(target, message) { target.textContent = message; target.hidden = false; }
    const start = document.getElementById('paymentStart');
    if (start) {
        const agreement = document.getElementById('purchaseAgreement');
        const error = document.getElementById('paymentError');
        const dialog = document.getElementById('samplePaymentDialog');
        const approve = document.getElementById('samplePaymentComplete');
        const sampleError = document.getElementById('samplePaymentError');
        const originalLabel = start.textContent;
        let order = null;
        let approving = false;
        const closeDialog = () => {
            if (approving) return;
            dialog.classList.remove('is-visible');
            setTimeout(() => dialog.close(), matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180);
        };
        dialog.querySelectorAll('[data-payment-close]').forEach(button => button.addEventListener('click', closeDialog));
        dialog.addEventListener('cancel', event => { event.preventDefault(); closeDialog(); });
        dialog.addEventListener('click', event => { if (event.target === dialog) {
            const rect = dialog.getBoundingClientRect();
            if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) closeDialog();
        } });
        start.addEventListener('click', async () => {
            error.hidden = true;
            if (!agreement.checked) { showError(error, '강의 제공 조건 및 환불 규정에 동의해 주세요.'); agreement.focus(); return; }
            start.disabled = true; start.textContent = '주문 확인 중…';
            try {
                order = await post(start.dataset.orderUrl, { lectureId: Number(start.dataset.lectureId), termsAgreed: true });
                if (!order) return;
                if (order.mode === 'MOCK') {
                    sampleError.hidden = true;
                    dialog.showModal(); requestAnimationFrame(() => dialog.classList.add('is-visible'));
                } else {
                    if (typeof window.TossPayments !== 'function') throw new Error('결제창을 불러오지 못했습니다. 네트워크 상태를 확인해 주세요.');
                    const payment = window.TossPayments(order.clientKey).payment({ customerKey: order.customerKey });
                    await payment.requestPayment({ method: 'CARD', amount: { currency: 'KRW', value: Number(order.amount) },
                        orderId: order.orderId, orderName: order.orderName, successUrl: order.successUrl, failUrl: order.failUrl,
                        card: { flowMode: 'DEFAULT' } });
                }
            } catch (failure) {
                showError(error, failure.code === 'USER_CANCEL' ? '결제창을 닫았습니다. 다시 결제할 수 있습니다.' : failure.message);
            } finally { start.disabled = false; start.textContent = originalLabel; }
        });
        approve.addEventListener('click', async () => {
            if (!order || approving) return;
            approving = true; approve.disabled = true; approve.textContent = '신청 처리 중…'; sampleError.hidden = true;
            try {
                const result = await post(start.dataset.approveUrl, { orderId: order.orderId });
                if (result) location.assign(result.redirectUrl);
            } catch (failure) { showError(sampleError, failure.message); }
            finally { approving = false; approve.disabled = false; approve.textContent = '결제완료'; }
        });
    }
    const confirmation = document.getElementById('tossConfirmation');
    if (confirmation) {
        const retry = document.getElementById('paymentRetry');
        const error = document.getElementById('confirmationError');
        async function confirm() {
            retry.disabled = true; error.hidden = true;
            try {
                const result = await post(confirmation.dataset.confirmUrl, { orderId: confirmation.dataset.orderId,
                    paymentKey: confirmation.dataset.paymentKey, amount: Number(confirmation.dataset.amount) });
                if (result) location.replace(result.redirectUrl);
            } catch (failure) {
                showError(error, failure.message); retry.hidden = false;
                document.querySelector('.mk-payment-spinner')?.classList.add('is-paused');
            } finally { retry.disabled = false; }
        }
        retry.addEventListener('click', confirm); confirm();
    }
})();
