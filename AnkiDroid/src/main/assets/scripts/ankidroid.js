"use strict";
globalThis.ankidroid = globalThis.ankidroid || {};

globalThis.ankidroid.userAction = function (number) {
    try {
        let userJs = globalThis[`userJs${number}`];
        if (userJs != null) {
            userJs();
        } else {
            window.location.href = `missing-user-action:${number}`;
        }
    } catch (e) {
        alert(e);
    }
};

globalThis.ankidroid.showHint = function () {
    document.querySelector("a.hint:not([style*='display: none'])")?.click();
};

globalThis.ankidroid.showAllHints = function () {
    document.querySelectorAll("a.hint").forEach(el => el.click());
};

(() => {
    let startX = 0,
        startY = 0,
        tapTimer = null,
        isSingleTouch = false;

    document.ontouchstart = function (event) {
        if (event.touches.length > 1) {
            isSingleTouch = false;
            return;
        }
        startX = event.touches[0].clientX;
        startY = event.touches[0].clientY;
        scrollX = window.scrollX;
        scrollY = window.scrollY;
        isSingleTouch = true;
    };

    document.ontouchend = function (event) {
        if (!isSingleTouch || isTextSelected() || isLink(event)) return;

        let endX = event.changedTouches[0].clientX,
            endY = event.changedTouches[0].clientY;

        if (tapTimer != null) {
            window.location.href = "tap://double";
            event.preventDefault();
            clearTimeout(tapTimer);
            tapTimer = null;
            return;
        }

        tapTimer = setTimeout(() => {
            const params = new URLSearchParams({
                x: endX,
                y: endY,
                deltaX: Math.abs(endX - startX),
                deltaY: Math.abs(endY - startY),
            });
            window.location.href = `tap://single/?${params.toString()}`;
            event.preventDefault();
            tapTimer = null;
        }, 200);
    };

    function isLink(e) {
        let node = e.target;
        while (node && node !== document) {
            const res =
                node.nodeName === "A" ||
                node.onclick ||
                node.nodeName === "BUTTON" ||
                node.nodeName === "VIDEO" ||
                node.nodeName === "SUMMARY" ||
                node.nodeName === "INPUT" ||
                node.getAttribute("contentEditable");
            if (res) {
                return true;
            }
            if (node.classList && node.classList.contains("tappable")) {
                return true;
            }
            node = node.parentNode;
        }
        return false;
    }

    function isTextSelected() {
        return !document.getSelection().isCollapsed;
    }
})();
