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
    const swipeDistance = 40;

    let startX = 0,
        startY = 0,
        scrollX = 0,
        scrollY = 0,
        tapTimer = null;


    document.ontouchstart = function (event) {
        startX = event.touches[0].clientX;
        startY = event.touches[0].clientY;
        scrollX = window.scrollX;
        scrollY = window.scrollY;
    };

    document.ontouchend = function (event) {
        if (tapTimer != null) {
            sendRequest("tap://double")
            preventEvent(event);

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
        if (isLink(event) || isTextSelected() || window.scrollX !== scrollX || window.scrollY !== scrollY) return;
        let endX = event.changedTouches[0].clientX,
            endY = event.changedTouches[0].clientY,
            deltaX = endX - startX,
            deltaY = endY - startY,
            absDeltaX = Math.abs(deltaX),
            absDeltaY = Math.abs(deltaY);

        if (absDeltaX > swipeDistance || absDeltaY > swipeDistance) {
            if (absDeltaX > absDeltaY) {
                if (deltaX > 0) {
                    sendRequest("swipe://right");
                } else {
                    sendRequest("swipe://left");
                }
            } else {
                if (deltaY > 0) {
                    sendRequest("swipe://down");
                } else {
                    sendRequest("swipe://up");
                }
            }
            preventEvent(event);
            return;
        }

        let column = Math.floor(endX / (window.innerWidth / 3)),
            row = Math.floor(endY / (window.innerHeight / 3));

        if (column < 0 || column > 2 || row < 0 || row > 2) return;

        let columnLabels = ["Left", "Center", "Right"],
            rowLabels = ["top", "mid", "bottom"];
        column = columnLabels[column];
        row = rowLabels[row];
        let target = row + column;

        sendRequest(`tap://${target}`);
        preventEvent(event);
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

    function preventEvent(e) {
        if (e.cancelable) {
            e.preventDefault();
        }
    }

    function sendRequest(request) {
        window.location.href = request;
    }
})();