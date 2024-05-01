"use strict";
globalThis.ankidroid = globalThis.ankidroid || {};

globalThis.ankidroid.userAction = function (number) {
    try {
        let userJs = globalThis[`userJs${number}`];
        if (userJs != null) {
            userJs();
        }
    } catch (e) {
        alert(e);
    }
};

globalThis.ankidroid.scale = 1;

(() => {
    const maxMovement = 20;

    let startX = 0,
        startY = 0,
        scrollX = 0,
        scrollY = 0,
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
            endY = event.changedTouches[0].clientY,
            deltaX = Math.abs(endX - startX),
            deltaY = Math.abs(endY - startY),
            threshold = maxMovement / globalThis.ankidroid.scale;

        if (deltaX > threshold || deltaY > threshold) return;

        if (tapTimer != null) {
            window.location.href = "tap://double";
            event.preventDefault();
            clearTimeout(tapTimer);
            tapTimer = null;
            return;
        }

        tapTimer = setTimeout(() => {
            let column = Math.floor(endX / (window.innerWidth / 3)),
                row = Math.floor(endY / (window.innerHeight / 3));

            if (column < 0 || column > 2 || row < 0 || row > 2) return;

            let columnLabels = ["Left", "Center", "Right"],
                rowLabels = ["top", "mid", "bottom"];
            column = columnLabels[column];
            row = rowLabels[row];
            let target = row + column;

            window.location.href = `tap://${target}`;
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
