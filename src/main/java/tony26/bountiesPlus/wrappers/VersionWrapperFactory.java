package tony26.bountiesPlus.wrappers;

import tony26.bountiesPlus.utils.VersionUtils;

public class VersionWrapperFactory {

    private static tony26.bountiesPlus.wrappers.VersionWrapper wrapper;

    public static tony26.bountiesPlus.wrappers.VersionWrapper getWrapper() {
        if (wrapper == null) {
            if (VersionUtils.isLegacy()) {
                wrapper = new LegacyVersionWrapper();
            } else {
                wrapper = new ModernVersionWrapper();
            }
        }
        return wrapper;
    }
}