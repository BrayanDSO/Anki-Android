var AnkiDroidJs = function(exports) {
  "use strict";var __defProp = Object.defineProperty;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);

  class Service {
    constructor(handler) {
      this.handler = handler;
    }
    async request(endpoint, data) {
      return this.handler.request(`${this.base}/${endpoint}`, data);
    }
  }
  class Android extends Service {
    constructor() {
      super(...arguments);
      __publicField(this, "base", "android");
    }
    /**
     * @returns whether the system is using night mode.
     */
    isSystemInDarkMode() {
      return this.request("is-system-in-dark-mode");
    }
    /**
     * @returns whether the active network is metered
     */
    isNetworkMetered() {
      return this.request("is-network-metered");
    }
  }
  class Card extends Service {
    constructor(handler, id = null) {
      super(handler);
      __publicField(this, "base", "card");
      /**
       * The card ID. If null, it will represent the queue's top card.
       */
      __publicField(this, "id");
      this.id = id;
    }
    /**
     * @returns The card ID.
     */
    getId() {
      if (this.id !== null) {
        return Promise.resolve({ success: true, value: this.id });
      }
      return this.request("get-id");
    }
    /**
     * Gets the flag of the current card.
     * 0: No flag, 1: Red, 2: Orange, 3: Green, 4: Blue, 5: Pink, 6: Turquoise, 7: Purple
     * @returns The card flag number.
     */
    getFlag() {
      return this.request("get-flag");
    }
    /**
     * Gets the `reps` property value of the card.
     * It represents the card's number of reviews.
     * @returns The card `reps` property
     */
    getReps() {
      return this.request("get-reps");
    }
    /**
     * Gets the card interval value.
     * Negative = seconds, positive = days.
     * v3 scheduler uses seconds only for intraday (re)learning cards
     * and days for interday (re)learning cards and review cards
     * @returns The card interval value.
     */
    getInterval() {
      return this.request("get-interval");
    }
    /**
     * @returns The ease factor of the card in permille (parts per thousand)
     */
    getFactor() {
      return this.request("get-factor");
    }
    /**
     * Gets the card's last modification time.
     * @returns The card `mod` property.
     */
    getMod() {
      return this.request("get-mod");
    }
    /**
     * Gets the ID of the note of this card.
     * @returns The card `nid` property
     */
    getNid() {
      return this.request("get-nid");
    }
    /**
     * Whether the card is New (0), Learning (1), Review (2), Relearning (3)
     * @returns The card `type` property
     */
    getType() {
      return this.request("get-type");
    }
    /**
     * Gets the ID of the deck containing this card.
     * @returns The card `did` property
     */
    getDid() {
      return this.request("get-did");
    }
    /**
     * Gets the card remaining steps
     * @returns The card `left` property
     */
    getLeft() {
      return this.request("get-left");
    }
    /**
     * Gets the ID of the original deck containing this card.
     * Only used when the card is currently in filtered deck.
     * @returns The card `odid` property
     */
    getODid() {
      return this.request("get-o-did");
    }
    /**
     * Gets the original due value of the card.
     * In filtered decks, it's the original due date that the card had before moving to filtered.
     * @returns The card `odue` property
     */
    getODue() {
      return this.request("get-o-due");
    }
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
    getQueue() {
      return this.request("get-queue");
    }
    /**
     * Gets the number of times the card went from a "was answered correctly" to "was answered incorrectly" state
     * @returns The card `lapses` property
     */
    getLapses() {
      return this.request("get-lapses");
    }
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
    getDue() {
      return this.request("get-due");
    }
    /**
     * @returns whether the card is marked
     */
    isMarked() {
      return this.request("is-marked");
    }
    /**
     * Buries the card.
     * @returns the number of buried cards.
     */
    bury() {
      return this.request("bury");
    }
    /**
     * Suspends the card.
     * @returns the number of suspended cards.
     */
    suspend() {
      return this.request("suspend");
    }
    /**
     * Resets the card progress.
     */
    resetProgress() {
      return this.request("reset-progress");
    }
    /**
     * Toggles a flag on the current card.
     * @param flag The flag to set on the card.
     */
    toggleFlag(flag) {
      if (flag < 0 || flag > 7) {
        return Promise.resolve({
          success: false,
          error: "Flag must be an integer between 0 and 7."
        });
      }
      return this.request("toggle-flag", { flag });
    }
    async request(endpoint, data) {
      return super.request(endpoint, { id: this.id, data });
    }
  }
  class Deck extends Service {
    constructor(handler, id = null) {
      super(handler);
      __publicField(this, "base", "deck");
      /**
       * The deck ID. If null, it will represent the deck of the queue's top card.
       */
      __publicField(this, "id");
      this.id = id;
    }
    /**
     * @returns The deck ID.
     */
    getId() {
      if (this.id !== null) {
        return Promise.resolve({ success: true, value: this.id });
      }
      return this.request("get-id");
    }
    /**
     * @returns The deck name.
     */
    getName() {
      return this.request("get-name");
    }
    /**
     * @returns whether the deck is a filtered deck.
     */
    isFiltered() {
      return this.request("is-filtered");
    }
    async request(endpoint, data) {
      return super.request(endpoint, { id: this.id, data });
    }
  }
  class Note extends Service {
    constructor(handler, id = null) {
      super(handler);
      __publicField(this, "base", "note");
      /**
       * The note ID. If null, it will represent the note of the queue's top card.
       */
      __publicField(this, "id");
      this.id = id;
    }
    /**
     * @returns The note ID.
     */
    getId() {
      if (this.id !== null) {
        return Promise.resolve({ success: true, value: this.id });
      }
      return this.request("get-id");
    }
    /**
     * Buries the note.
     * @returns the number of buried cards.
     */
    bury() {
      return this.request("bury");
    }
    /**
     * Suspends the note.
     @returns the number of suspended cards.
     */
    suspend() {
      return this.request("suspend");
    }
    getTags() {
      return this.request("get-tags");
    }
    setTags(tags) {
      return this.request("set-tags", { tags });
    }
    /**
     * Toggles the "marked" status of the note.
     */
    toggleMark() {
      return this.request("toggle-mark");
    }
    async request(endpoint, data) {
      return super.request(endpoint, { id: this.id, data });
    }
  }
  class Tts extends Service {
    constructor() {
      super(...arguments);
      __publicField(this, "base", "tts");
    }
    /**
     * Speaks the text using the specified queuing strategy.
     * This method is asynchronous, i.e. the method just adds the request to the queue of TTS requests and then returns.
     *
     * @param text the string of text to be spoken.
     * @param queueMode how the new entry is added to the playback queue.
     * * 0 (QUEUE_FLUSH): all entries are dropped and replaced by the new entry.
     * * 1 (QUEUE_ADD): the new entry is added at the end of the playback queue.
     *
     * @returns 0 (SUCCESS) or -1 (ERROR)
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#speak(kotlin.CharSequence,%20kotlin.Int,%20android.os.Bundle,%20kotlin.String)
     */
    speak(text, queueMode) {
      if (queueMode !== 0 && queueMode !== 1) {
        return Promise.resolve({ success: false, error: "Invalid queue mode." });
      }
      return this.request("speak", { text, queueMode });
    }
    /**
     * Sets the text-to-speech language. The TTS engine will try to use the closest match to the specified language
     * as represented by the Locale, but there is no guarantee that the exact same Locale will be used
     *
     * @param locale specifying the language to speak.
     *
     * @returns Code indicating the support status for the locale
     * *  0 (LANG_AVAILABLE): language is available for the language by the locale, but not the country and variant.
     * *  1 (LANG_COUNTRY_AVAILABLE): language is available for the language and country specified by the locale, but not the variant.
     * *  2 (LANG_COUNTRY_VAR_AVAILABLE): language is available exactly as specified by the locale.
     * * -1 (LANG_MISSING_DATA): language data is missing.
     * * -2 (LANG_NOT_SUPPORTED): language is not supported.
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#setlanguage
     */
    setLanguage(locale) {
      return this.request("set-language", { locale });
    }
    /**
     * Sets the speech pitch for the TextToSpeech engine.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice,
     *   greater values increase it.
     *
     * @returns 0 (SUCCESS) or -1 (ERROR)
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#setpitch
     */
    setPitch(pitch) {
      return this.request("set-pitch", { pitch });
    }
    /**
     * Sets the speech rate.
     *
     * @param speechRate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     *   (0.5 is half the normal speech rate), greater values accelerate it (2.0 is twice the normal speech rate).
     *
     * @returns 0 (SUCCESS) or -1 (ERROR)
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#setspeechrate
     */
    setSpeechRate(speechRate) {
      return this.request("set-speech-rate", { speechRate });
    }
    /**
     * @returns whether the TTS engine is busy speaking.
     */
    isSpeaking() {
      return this.request("is-speaking");
    }
    /**
     * Interrupts the current utterance
     * and discards other utterances in the queue.
     *
     * @returns 0 (SUCCESS) or -1 (ERROR)
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#stop
     */
    stop() {
      return this.request("stop");
    }
  }
  class StudyScreen extends Service {
    constructor() {
      super(...arguments);
      __publicField(this, "base", "study-screen");
    }
    /**
     * Shows an Android dismissable snackbar.
     * @param text the message to be shown in the snackbar.
     * @param duration the duration in milliseconds to show the snackbar.
     * For Android standard durations, use 0 for long, -1 for short, and -2 for indefinite.
     */
    showSnackbar(text, duration) {
      return this.request("show-snackbar", { text, duration });
    }
    /**
     * @returns the count of New cards in the queue.
     */
    getNewCount() {
      return this.request("get-new-count");
    }
    /**
     * @returns the count of Learning cards in the queue.
     */
    getLearningCount() {
      return this.request("get-learning-count");
    }
    /**
     * @returns the count of To Review cards in the queue.
     */
    getToReviewCount() {
      return this.request("get-to-review-count");
    }
    /**
     * Show the card answer
     */
    showAnswer() {
      return this.request("show-answer");
    }
    /**
     * @returns whether the answer is being shown.
     */
    isShowingAnswer() {
      return this.request("is-showing-answer");
    }
    /**
     *
     * @param rating
     */
    getNextTime(rating) {
      return this.request("get-next-time", { rating });
    }
    /**
     * Opens the card info of the specified ID.
     * @param cardId the ID of the card.
     */
    openCardInfo(cardId) {
      return this.request("open-card-info", { cardId });
    }
    /**
     * Opens the note editor to edit the note of the specified card ID.
     * @param cardId the ID of the card.
     */
    openNoteEditor(cardId) {
      return this.request("open-note-editor", { cardId });
    }
    /**
     * Search the given query in the card browser.
     * @param query the query to be searched in the browser
     */
    search(query) {
      return this.request("search", { query });
    }
    /**
     * Sets the background color of the Study screen itself,
     * which includes the toolbar and system bars (if they are transparent).
     * This does not change the card background color.
     *
     * @param hexCode the hexadecimal RGB code of the color.
     *
     * Supported formats are:
     * ```
     * #RRGGBB
     * #AARRGGBB
     * ```
     * A: Alpha; R: Red; G: Green; B: Blue.
     */
    setBackgroundColor(hexCode) {
      return this.request("set-background-color", { hexCode });
    }
    /**
     * Answers the top card with the specified rating.
     * @param rating the rating to answer the card.
     */
    answer(rating) {
      return this.request("answer", { rating });
    }
    /**
     * Undo the last action.
     */
    undo() {
      return this.request("undo");
    }
    /**
     * Deletes the current note.
     */
    deleteNote() {
      return this.request("delete-note");
    }
  }
  class Api {
    constructor(contract) {
      __publicField(this, "android");
      __publicField(this, "card");
      __publicField(this, "note");
      __publicField(this, "deck");
      __publicField(this, "studyScreen");
      __publicField(this, "tts");
      this.contract = contract;
      this.android = new Android(this);
      this.card = new Card(this);
      this.note = new Note(this);
      this.deck = new Deck(this);
      this.studyScreen = new StudyScreen(this);
      this.tts = new Tts(this);
    }
    /**
     * Gets a Card instance for a specific card ID.
     */
    getCard(id) {
      return new Card(this, id);
    }
    /**
     * Gets a Note instance for a specific note ID.
     */
    getNote(id) {
      return new Note(this, id);
    }
    /**
     * Gets a Deck instance for a specific deck ID.
     */
    getDeck(id) {
      return new Deck(this, id);
    }
    async request(endpoint, data) {
      const url = `/jsapi/${endpoint}`;
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          version: this.contract.version,
          developer: this.contract.developer,
          data
        })
      });
      if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`);
      }
      const responseData = await response.text();
      return JSON.parse(responseData);
    }
  }
  exports.Api = Api;
  Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
  return exports;
}({});
