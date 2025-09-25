package dev.byflow.customtntflow.api;

public final class MutableBlockBehavior {
    private boolean breakBlocks = true;
    private boolean dropBlocks = true;
    private boolean apiOnly;

    public void setBreakBlocks(boolean breakBlocks) {
        this.breakBlocks = breakBlocks;
    }

    public void setDropBlocks(boolean dropBlocks) {
        this.dropBlocks = dropBlocks;
    }

    public void setApiOnly(boolean apiOnly) {
        this.apiOnly = apiOnly;
    }

    public boolean shouldBreakBlocks() {
        return breakBlocks;
    }

    public boolean shouldDropBlocks() {
        return dropBlocks;
    }

    public boolean isApiOnly() {
        return apiOnly;
    }
}
