(function () {
    function setupForm(form) {
        if (!form || form.getAttribute('data-simulated-payment-form') !== 'true') {
            return;
        }

        var newCardSection = form.querySelector('[data-new-card-section="true"]');
        var savedCardInputs = form.querySelectorAll('input[name="selectedCardId"]');
        var newCardInputs = form.querySelectorAll('[data-new-card-input="true"]');
        var numberDisplay = form.querySelector('[data-card-number-display="true"]');
        var providerTokenInput = form.querySelector('[data-provider-token="true"]');
        var lastFourInput = form.querySelector('[data-last-four="true"]');

        function usingNewCard() {
            if (!savedCardInputs.length) {
                return true;
            }
            for (var i = 0; i < savedCardInputs.length; i++) {
                if (savedCardInputs[i].checked) {
                    return savedCardInputs[i].value === '';
                }
            }
            return false;
        }

        function syncState() {
            var newCardActive = usingNewCard();
            if (newCardSection) {
                newCardSection.classList.toggle('hidden', !newCardActive);
            }

            if (numberDisplay) numberDisplay.disabled = !newCardActive;
            newCardInputs.forEach(function (input) {
                input.disabled = !newCardActive;
                if (newCardActive) {
                    input.setAttribute('required', 'required');
                } else {
                    input.removeAttribute('required');
                }
            });
        }

        if (numberDisplay) {
            numberDisplay.addEventListener('input', function () {
                var digits = numberDisplay.value.replace(/\D/g, '').slice(0, 19);
                var groups = digits.match(/.{1,4}/g) || [];
                numberDisplay.value = groups.join(' ');
            });
        }

        savedCardInputs.forEach(function (input) {
            input.addEventListener('change', syncState);
        });

        form.addEventListener('submit', function (event) {
            if (!usingNewCard()) {
                if (providerTokenInput) providerTokenInput.value = '';
                if (lastFourInput) lastFourInput.value = '';
                return;
            }

            var rawDigits = numberDisplay ? numberDisplay.value.replace(/\D/g, '') : '';
            if (rawDigits.length < 12) {
                event.preventDefault();
                window.alert('Please enter a valid demo card number.');
                return;
            }

            var lastFour = rawDigits.slice(-4);
            if (lastFourInput) {
                lastFourInput.value = lastFour;
            }
            if (providerTokenInput) {
                providerTokenInput.value = 'sim_' + lastFour + '_' + Date.now();
            }

            if (numberDisplay) {
                numberDisplay.value = '';
            }
        });

        syncState();
    }

    document.querySelectorAll('form[data-simulated-payment-form]').forEach(setupForm);
})();
