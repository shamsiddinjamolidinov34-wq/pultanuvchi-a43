package uz.pultanuvchi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Bitta banknota turi haqida ma'lumot. */
public final class Banknote {
    /** Qiymati, so'mda. */
    public final int value;
    /** Ko'rinish uchun, masalan "10 000". */
    public final String digits;
    /** Banknotada bosilgan raqam, masalan "10000". */
    public final String numeral;
    /** Ovoz uchun matn (kichik harf, hozirgi alifbo). */
    public final String spoken;
    /** Banknotadagi so'z yozuvining ASCII "skeleti" (eski va yangi alifbo uchun bir xil). */
    public final List<String> phrases;

    public Banknote(int value, String digits, String numeral, String spoken, String... phrases) {
        this.value = value;
        this.digits = digits;
        this.numeral = numeral;
        this.spoken = spoken;
        this.phrases = Collections.unmodifiableList(Arrays.asList(phrases));
    }
}
