"use strict";
(() => {
    // src/types/card.ts
    var cardEndpoints = {
        GET_ID: "getId",
        IS_MARKED: "isMarked",
        GET_FLAG: "getFlag",
        GET_REPS: "getReps",
        GET_INTERVAL: "getInterval",
        GET_FACTOR: "getFactor",
        GET_MOD: "getMod",
        GET_NID: "getNid",
        GET_TYPE: "getType",
        GET_DID: "getDid",
        GET_LEFT: "getLeft",
        GET_ODID: "getOdid",
        GET_ODUE: "getOdue",
        GET_QUEUE: "getQueue",
        GET_LAPSES: "getLapses",
        GET_DUE: "getDue",
        BURY: "bury",
        SUSPEND: "suspend",
        RESET_PROGRESS: "resetProgress",
        TOGGLE_MARK: "toggleMark",
        TOGGLE_FLAG: "toggleFlag",
    };
    var Card = class {
        constructor(handler, id) {
            this.handler = handler;
            this.id = id;
            /**
             * Gets the card ID.
             * It's originated from the epoch milliseconds of when the card was created.
             * @returns The card `id` property.
             */
            this.getId = () => this.handleRequest(cardEndpoints.GET_ID);
            /**
             * @returns whether the card is marked
             */
            this.isMarked = () => this.handleRequest(cardEndpoints.IS_MARKED);
            /**
             * Gets the flag of the current card.
             * 0: No flag, 1: Red, 2: Orange, 3: Green, 4: Blue, 5: Pink, 6: Turquoise, 7: Purple
             * @returns The card flag number.
             */
            this.getFlag = () => this.handleRequest(cardEndpoints.GET_FLAG);
            /**
             * Gets the `reps` property value of the card.
             * It represents the card's number of reviews.
             * @returns The card `reps` property
             */
            this.getReps = () => this.handleRequest(cardEndpoints.GET_REPS);
            /**
             * Gets the card interval value.
             * Negative = seconds, positive = days.
             * v3 scheduler uses seconds only for intraday (re)learning cards
             * and days for interday (re)learning cards and review cards
             * @returns The card interval value.
             */
            this.getInterval = () => this.handleRequest(cardEndpoints.GET_INTERVAL);
            /**
             * @returns The ease factor of the card in permille (parts per thousand)
             */
            this.getFactor = () => this.handleRequest(cardEndpoints.GET_FACTOR);
            /**
             * Gets the card's last modification time.
             * @returns The card `mod` property.
             */
            this.getMod = () => this.handleRequest(cardEndpoints.GET_MOD);
            /**
             * Gets the ID of the note of this card.
             * @returns The card `nid` property
             */
            this.getNid = () => this.handleRequest(cardEndpoints.GET_NID);
            /**
             * Whether the card is New (0), Learning (1), Review (2), Relearning (3)
             * @returns The card `type` property
             */
            this.getType = () => this.handleRequest(cardEndpoints.GET_TYPE);
            /**
             * Gets the ID of the deck containing this card.
             * @returns The card `did` property
             */
            this.getDid = () => this.handleRequest(cardEndpoints.GET_DID);
            /**
             * Gets the card remaining steps
             * @returns The card `left` property
             */
            this.getLeft = () => this.handleRequest(cardEndpoints.GET_LEFT);
            /**
             * Gets the ID of the original deck containing this card.
             * Only used when the card is currently in filtered deck.
             * @returns The card `odid` property
             */
            this.getODid = () => this.handleRequest(cardEndpoints.GET_ODID);
            /**
             * Gets the original due value of the card.
             * In filtered decks, it's the original due date that the card had before moving to filtered.
             * @returns The card `odue` property
             */
            this.getODue = () => this.handleRequest(cardEndpoints.GET_ODUE);
            /**
             * Gets the `queue` property of the card. It can be:
             * * -3 = buried by the user,
             * * -2 = buried by the scheduler,
             * * -1 = suspended
             * * 0 = new
             * * 1 = learning
             * * 2 = review
             * * 3 = in learning, next review in at least a day after the previous review
             * * 4 = review
             * @returns The card `queue` property
             */
            this.getQueue = () => this.handleRequest(cardEndpoints.GET_QUEUE);
            /**
             * Gets the number of times the card went from a "was answered correctly" to "was answered incorrectly" state
             * @returns The card `lapses` property
             */
            this.getLapses = () => this.handleRequest(cardEndpoints.GET_LAPSES);
            /**
             * Gets the card due value.
             * Due is used differently for different card types:
             * * New: the order in which cards are to be studied; starts from 1.
             * * Learning/relearning: epoch timestamp in seconds
             * * Review: days since the collection's creation time
             * * Filtered decks: the position of the card in the filtered deck.
             * The value of due before moving to the filtered deck is saved as odue.
             * @returns The card `due` property
             */
            this.getDue = () => this.handleRequest(cardEndpoints.GET_DUE);
            /**
             * Buries the card.
             */
            this.bury = () => this.handleRequest(cardEndpoints.BURY);
            /**
             * Suspends the card.
             */
            this.suspend = () => this.handleRequest(cardEndpoints.SUSPEND);
            /**
             * Resets the card progress.
             */
            this.resetProgress = () => this.handleRequest(cardEndpoints.RESET_PROGRESS);
            /**
             * Toggles the "marked" status of the card.
             */
            this.toggleMark = () => this.handleRequest(cardEndpoints.TOGGLE_MARK);
            /**
             * Toggles a flag on the current card.
             * @param flag The flag to set on the card.
             */
            this.toggleFlag = flag => {
                if (flag < 0 || flag > 7) {
                    console.error("toggleFlag: flag must be an integer between 0 and 7.");
                    return Promise.resolve();
                }
                return this.handleRequest(cardEndpoints.TOGGLE_FLAG, { flag });
            };
        }
        async handleRequest(endpoint, data) {
            const id = this.id;
            return this.handler.request(`card/${endpoint}`, { id, data });
        }
    };

    // src/types/note.ts
    var noteEndpoints = {
        GET_ID: "getId",
        BURY: "bury",
        SUSPEND: "suspend",
        GET_TAGS: "getTags",
        SET_TAGS: "setTags",
    };
    var Note = class {
        constructor(handler, id) {
            this.handler = handler;
            this.id = id;
            /**
             * Gets the note ID.
             * It's originated from the epoch milliseconds of when the note was created.
             * @returns The note `id` property.
             */
            this.getId = () => this.handleRequest(noteEndpoints.GET_ID);
            /**
             * Buries the note.
             */
            this.bury = () => this.handleRequest(noteEndpoints.BURY);
            /**
             * Suspends the note.
             */
            this.suspend = () => this.handleRequest(noteEndpoints.SUSPEND);
            this.getTags = () => this.handleRequest(noteEndpoints.GET_TAGS);
            this.setTags = tags => this.handleRequest(noteEndpoints.SET_TAGS, { tags });
        }
        async handleRequest(endpoint, data) {
            const id = this.id;
            return this.handler.request(`note/${endpoint}`, { id, data });
        }
    };

    // src/types/deck.ts
    var deckEndpoints = {
        GET_ID: "getId",
        GET_NAME: "getName",
    };
    var Deck = class {
        constructor(handler, id) {
            this.handler = handler;
            this.id = id;
            /**
             * @returns The deck ID.
             */
            this.getId = () => this.handleRequest(deckEndpoints.GET_ID);
            /**
             * @returns The deck name.
             */
            this.getName = () => this.handleRequest(deckEndpoints.GET_NAME);
        }
        async handleRequest(endpoint, data) {
            const id = this.id;
            return this.handler.request(`deck/${endpoint}`, { id, data });
        }
    };

    // src/types/android.ts
    var androidEndpoints = {
        IS_DARK_MODE_ENABLED: "isDarkModeEnabled",
        SHOW_SNACKBAR: "showSnackbar",
    };
    var Android = class {
        constructor(handler) {
            this.handler = handler;
            this.statusBar = {
                setColor: hexCode => this.handleRequest("android/statusBar/setColor", hexCode),
                isShown: () => this.handleRequest("android/statusBar/isShown"),
                show: isShown => this.handleRequest("android/statusBar/show", isShown),
            };
            this.navigationBar = {
                setColor: hexCode => this.handleRequest("android/navBar/setColor", hexCode),
                isShown: () => this.handleRequest("android/navBar/isShown"),
                show: isShown => this.handleRequest("android/navBar/show", isShown),
            };
            /**
             * @returns whether the current theme is dark
             */
            this.isDarkModeEnabled = () =>
                this.handleRequest(androidEndpoints.IS_DARK_MODE_ENABLED);
            /**
             * Shows an Android dismissable snackbar.
             * @param message the message to be shown in the snackbar.
             * @param duration the duration in milliseconds to show the snackbar.
             * For Android standard durations, use 0 for long, -1 for short, and -2 for indefinite.
             */
            this.showSnackbar = (message, duration) =>
                this.handleRequest(androidEndpoints.SHOW_SNACKBAR, { message, duration });
        }
        async handleRequest(endpoint, data) {
            return this.handler.request(`android/${endpoint}`, data);
        }
    };

    // src/main.ts
    var jsApiList = {
        ankiIsActiveNetworkMetered: "isActiveNetworkMetered",
        ankiSearchCard: "searchCard",
        ankiSearchCardWithCallback: "searchCardWithCallback",
        ankiSttSetLanguage: "sttSetLanguage",
        ankiSttStart: "sttStart",
        ankiSttStop: "sttStop",
        ankiTtsSpeak: "ttsSpeak",
        ankiTtsSetLanguage: "ttsSetLanguage",
        ankiTtsSetPitch: "ttsSetPitch",
        ankiTtsSetSpeechRate: "ttsSetSpeechRate",
        ankiTtsFieldModifierIsAvailable: "ttsFieldModifierIsAvailable",
        ankiTtsIsSpeaking: "ttsIsSpeaking",
        ankiTtsStop: "ttsStop",
    };
    var StudyScreen = class {
        constructor(contract) {
            this.contract = contract;
            this.currentCard = new Card(this, 0);
            this.currentNote = new Note(this, 0);
            this.currentDeck = new Deck(this, 0);
            this.android = new Android(this);
        }
        async request(endpoint, data) {
            const url = `/jsapi/${endpoint}`;
            try {
                const response = await fetch(url, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        author: this.contract.author,
                        supportUrl: this.contract.supportUrl,
                        data,
                    }),
                });
                if (!response.ok) {
                    throw new Error("Failed to make the request");
                }
                const responseData = await response.text();
                return JSON.parse(responseData);
            } catch (error) {
                console.error("Request error:", error);
                throw error;
            }
        }
        // public readonly counts: Counts = {
        //     getNew: () => this.handleRequest("counts/new"),
        //     getLrn: () => this.handleRequest("counts/lrn"),
        //     getRev: () => this.handleRequest("counts/rev"),
        // };
        //
        // /**
        //  * Reveals the answer side of the current card.
        //  */
        // public showAnswer(): Promise<void> {
        //     return this.handleRequest("showAnswer");
        // }
        //
        // /**
        //  * Answers the current card with the given ease.
        //  * @param ease The ease rating for the card (1-4).
        //  */
        // public answer(ease: Ease): Promise<void> {
        //     return this.handleRequest("answer", ease);
        // }
        //
        // /**
        //  * @returns whether the answer is being shown
        //  */
        // public isShowingAnswer(): Promise<BooleanResult> {
        //     return this.handleRequest("isShowingAnswer");
        // }
        //
        // public getNextTime(ease: Ease): Promise<StringResult> {
        //     return this.handleRequest("getNextTime", ease);
        // }
        //
        // public cardInfo(id: CardId | undefined): Promise<void> {
        //     return this.handleRequest(endpoints.CARD_INFO, id);
        // }
        //
        // public editCard(id: CardId | undefined): Promise<void> {
        //     return this.handleRequest(endpoints.EDIT_CARD, id);
        // }
    };
    globalThis.jsApi = { StudyScreen };
})();
