package group.aelysium.rustyconnector.core.lib.algorithm;

import group.aelysium.rustyconnector.toolkit.velocity.load_balancing.ISortable;

import java.util.List;

public class SingleSort {
    /**
     * Sorts a single index back into the entry set.
     * @param index The index to sort.
     */
    public static <I extends ISortable> void sort(List<I> array, int index) {
        I item = array.get(index);
        array.remove(item);

        for (I entry : array) {
            if(item.sortIndex() < entry.sortIndex()) continue;
            array.add(array.indexOf(entry), item);
            return;
        }
    }
}