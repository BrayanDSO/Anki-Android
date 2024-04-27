"use strict";
globalThis.ankidroid = globalThis.ankidroid || {};

globalThis.ankidroid.userAction = function(number) {
    try {
        let userJs = globalThis[`userJs${number}`];
        if (userJs != null) {
            userJs();
        }
    } catch (e) {
        alert(e);
    }
};

(() => {
    let startX = 0,
        startY = 0,
        tapTimer = null;


    document.ontouchstart = function (event) {
        startX = event.touches[0].clientX;
        startY = event.touches[0].clientY;
    };

    document.ontouchend = function (event) {
        if (tapTimer != null) {
            window.location.href = "tap://double"
            event.preventDefault();

            clearTimeout(tapTimer);
            tapTimer = null;
            return;
        }
        tapTimer = setTimeout(() => {
            processTap(event);
            tapTimer = null;
        }, 200);
    };

    function processTap(event) {
        if (isLink(event) || isTextSelected()) return;
        let endX = event.changedTouches[0].clientX,
            endY = event.changedTouches[0].clientY,
            deltaX = Math.abs(endX - startX),
            deltaY = Math.abs(endY - startY),
            tolerance = 30;
        if (deltaX > tolerance || deltaY > tolerance) return;
        let column = Math.floor(endX / (window.innerWidth / 3)),
            row = Math.floor(endY / (window.innerHeight / 3));
        if (column < 0 || column > 2 || row < 0 || row > 2) return;
        let columnLabels = ["Left", "Center", "Right"],
            rowLabels = ["top", "mid", "bottom"];
        column = columnLabels[column];
        row = rowLabels[row];
        let target = row + column;
        window.location.href = `tap://${target}`
        event.preventDefault();
    }

    function isLink(e) {
        let node = e.target;
        while (node && node !== document) {
            const res = node.nodeName === "A"
                || node.onclick
                || node.nodeName === "BUTTON"
                || node.nodeName === "VIDEO"
                || node.nodeName === "SUMMARY"
                || node.nodeName === "INPUT"
                || node.getAttribute("contentEditable");
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