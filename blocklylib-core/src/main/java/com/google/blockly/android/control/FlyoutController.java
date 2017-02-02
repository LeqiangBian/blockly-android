/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.blockly.android.control;

import android.view.View;

import com.google.blockly.android.CategorySelectorFragment;
import com.google.blockly.android.FlyoutFragment;
import com.google.blockly.android.ui.BlockGroup;
import com.google.blockly.android.ui.CategoryTabs;
import com.google.blockly.android.ui.FlyoutCallback;
import com.google.blockly.android.ui.OnDragToTrashListener;
import com.google.blockly.model.Block;
import com.google.blockly.model.FlyoutCategory;
import com.google.blockly.model.WorkspacePoint;

import java.util.List;

/**
 * Provides control logic for the toolbox and trash in a workspace. Ensures the toolbox and trash
 * block-list flyouts are populated, opened, and closed in coordination.
 */
public class FlyoutController {
    private static final String TAG = "FlyoutController";
    /// Whether the toolbox is currently closeable, depending on configuration.
    protected boolean mToolboxIsCloseable = true;
    /// The fragment for displaying toolbox categories
    protected CategorySelectorFragment mCategoryFragment;
    /// The fragment for displaying blocks in the current category
    protected FlyoutFragment mToolboxFlyout;
    /// The root of the toolbox tree, containing either blocks or subcategories (not both).
    protected FlyoutCategory mToolboxRoot;

    /// Whether the trash is closeable, depending on configuration.
    protected boolean mTrashIsCloseable = true;
    /// The fragment for displaying blocks in the trash.
    protected FlyoutFragment mTrashFlyout;
    /// The category backing the trash's list of blocks.
    protected FlyoutCategory mTrashCategory;

    /// Main controller for any actions that require wider state changes.
    protected BlocklyController mController;

    /// Callbacks for user actions on the toolbox's flyout
    protected FlyoutCallback mToolboxFlyoutCallback = new FlyoutCallback() {
        @Override
        public void onButtonClicked(View v, String action, FlyoutCategory category) {
            // TODO (#503): Switch to using the view's tag to determine behavior
            if (category != null && category.isVariableCategory() && mController != null) {
                mController.requestAddVariable("item");
            }
        }

        @Override
        public BlockGroup getDraggableBlockGroup(int index, Block blockInList,
                WorkspacePoint initialBlockPosition) {
            Block copy = blockInList.deepCopy();
            copy.setPosition(initialBlockPosition.x, initialBlockPosition.y);
            BlockGroup copyView = mController.addRootBlock(copy);
            closeToolbox();
            return copyView;
        }
    };

    /// Callbacks for user actions on the list of categories in the Toolbox
    protected CategoryTabs.Callback mTabsCallback = new CategoryTabs.Callback() {
        @Override
        public void onCategoryClicked(FlyoutCategory category) {
            FlyoutCategory currCategory = mCategoryFragment.getCurrentCategory();
            if (category == currCategory) {
                // Clicked the open category, close it if closeable.
                closeToolbox();
            } else {
                setToolboxCategory(category);
                closeTrash();
            }

        }
    };


    /// Callbacks for user actions on the trash's flyout
    protected FlyoutCallback mTrashFlyoutCallback = new FlyoutCallback() {
        @Override
        public void onButtonClicked(View v, String action, FlyoutCategory category) {
            // No actions recognized by the trash
        }

        @Override
        public BlockGroup getDraggableBlockGroup(int index, Block blockInList,
                WorkspacePoint initialBlockPosition) {
            Block copy = blockInList.deepCopy();
            copy.setPosition(initialBlockPosition.x, initialBlockPosition.y);
            BlockGroup copyView = mController.addRootBlock(copy);
            mTrashCategory.removeBlock(blockInList);
            closeTrash();
            return copyView;
        }
    };

    /// Opens/closes the trash in response to clicks on the trash icon.
    protected View.OnClickListener mTrashClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Toggle opened state.
            if (mTrashFlyout.isOpen()) {
                closeTrash();
            } else {
                mTrashFlyout.setCurrentCategory(mTrashCategory);
                closeToolbox();
            }
        }
    };

    public FlyoutController(BlocklyController controller) {
        mController = controller;
    }

    /**
     * Sets the fragments used by a toolbox. At minimum a flyout is needed. If a category fragment
     * is provided it will be used to switch between categories and open/close the flyout if it
     * is closeable.
     *
     * @param categoryFragment The fragment for displaying category tabs.
     * @param toolboxFlyout The fragment for displaying blocks in a category.
     */
    public void setToolboxFragments(CategorySelectorFragment categoryFragment,
            FlyoutFragment toolboxFlyout) {
        mCategoryFragment = categoryFragment;
        mToolboxFlyout = toolboxFlyout;
        if (mToolboxFlyout == null) {
            return;
        }

        if (mCategoryFragment != null) {
            mCategoryFragment.setCategoryCallback(mTabsCallback);
        }
        mToolboxIsCloseable = mToolboxFlyout.isCloseable();
        if (mToolboxRoot != null) {
            setToolboxRoot(mToolboxRoot);
        }
        mToolboxFlyout.init(mController, mToolboxFlyoutCallback);
        updateToolbox();
    }

    /**
     * Sets the root of the toolbox tree. This will be used to populate the category and toolbox
     * flyout.
     *
     * @param root The root category for the toolbox.
     */
    public void setToolboxRoot(FlyoutCategory root) {
        mToolboxRoot = root;
        if (mToolboxRoot == null) {
            if (mCategoryFragment != null) {
                mCategoryFragment.setContents(null);
            }
            if (mToolboxFlyout != null) {
                mToolboxFlyout.setCurrentCategory(null);
            }
            return;
        }
        updateToolbox();
    }

    /**
     * Closes the trash and toolbox if they're open and closeable.
     *
     * @return True if either flyout was closed, false otherwise.
     */
    public boolean closeFlyouts() {
        return closeTrash() || closeToolbox();
    }

    /**
     * @return True if the toolbox's flyout may be closed.
     */
    public boolean isToolboxCloseable() {
        return mToolboxIsCloseable && mCategoryFragment != null;
    }

    /**
     * @return True if the trash's flyout may be closed.
     */
    public boolean isTrashCloseable() {
        return mTrashIsCloseable;
    }

    /**
     * @param trashFragment The flyout to use for displaying blocks in the trash.
     */
    public void setTrashFragment(FlyoutFragment trashFragment) {
        mTrashFlyout = trashFragment;
        if (trashFragment != null) {
            mTrashIsCloseable = mTrashFlyout.isCloseable();
            mTrashFlyout.init(mController, mTrashFlyoutCallback);
            closeTrash();
        }
    }

    /**
     * @param trashContents The category with the set of blocks for display in the trash.
     */
    public void setTrashContents(FlyoutCategory trashContents) {
        mTrashCategory = trashContents;
        if (mTrashFlyout != null) {
            mTrashFlyout.setCurrentCategory(mTrashFlyout.isOpen() ? trashContents : null);
        }
    }

    /**
     * @param trashIcon The view for toggling the trash.
     */
    public void setTrashIcon(View trashIcon) {
        if (trashIcon == null) {
            return;
        }
        // The trash icon is always a drop target.
        trashIcon.setOnDragListener(new OnDragToTrashListener(mController));
        if (mTrashFlyout != null && mTrashIsCloseable) {
            // But we only need a click listener if the trash can be closed.
            trashIcon.setOnClickListener(mTrashClickListener);
        }
    }

    /**
     * Updates the contents of toolbox and ensures it's open if it's not closeable.
     */
    private void updateToolbox() {
        if (mToolboxRoot == null) {
            return;
        }
        List<FlyoutCategory> subCats = mToolboxRoot.getSubcategories();
        List<Block> topBlocks = mToolboxRoot.getBlocks();
        if (subCats.size() > 0 && topBlocks.size() > 0) {
            throw new IllegalArgumentException(
                    "Toolbox root cannot have both blocks and subcategories.");
        }
        if (mToolboxRoot.getSubcategories().size() == 0) {
            FlyoutCategory newRoot = new FlyoutCategory();
            newRoot.addSubcategory(mToolboxRoot);
            mToolboxRoot = newRoot;
        }
        if (mCategoryFragment != null) {
            mCategoryFragment.setContents(mToolboxRoot);
        }
        if (!mToolboxIsCloseable) {
            setToolboxCategory(mToolboxRoot.getSubcategories().get(0));
        } else {
            closeToolbox();
        }
    }

    /**
     * Handles setting the category on the toolbox flyout and the category fragment if they exist.
     *
     * @param category The category to set.
     */
    private void setToolboxCategory(FlyoutCategory category) {
        if (mToolboxFlyout != null) {
            mToolboxFlyout.setCurrentCategory(category);
        }
        if (mCategoryFragment != null) {
            mCategoryFragment.setCurrentCategory(category);
        }
    }

    /**
     * Handles checking if the toolbox is closeable and closing it if so.
     *
     * @return true if the toolbox was closed, false otherwise.
     */
    private boolean closeToolbox() {
        boolean didClose = false;
        if (isToolboxCloseable() && mToolboxFlyout != null) {
            didClose = mToolboxFlyout.closeBlocksDrawer();
            if (mCategoryFragment != null) {
                mCategoryFragment.setCurrentCategory(null);
            }
        }
        return didClose;
    }

    /**
     * Handles checking and closing the trash flyout.
     *
     * @return true if the trash was closed, false otherwise.
     */
    private boolean closeTrash() {
        boolean didClose = false;
        if (isTrashCloseable() && mTrashFlyout != null) {
            didClose = mTrashFlyout.closeBlocksDrawer();
        }
        return didClose;
    }
}
