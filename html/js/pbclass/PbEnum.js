// PbEnum.js — port of Java PbEnum.java
export class PbEnum {
    constructor() {
        /** @type {number} */
        this.index = 0;
        /** @type {string} */
        this.name  = '';
        /** @type {Map<number, string>} */
        this.items = new Map();
    }
}
