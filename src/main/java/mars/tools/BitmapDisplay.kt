package mars.tools

import mars.mips.hardware.AccessNotice
import mars.mips.hardware.Memory
import mars.mips.hardware.MemoryAccessNotice
import mars.util.Binary
import java.awt.*
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/*
Copyright (c) 2010-2011,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */
/**
 * Bitmapp display simulator.  It can be run either as a stand-alone Java application having access to the mars package,
 * or through MARS as an item in its Tools menu.  It makes maximum use of methods inherited from its abstract superclass
 * AbstractMarsToolAndApplication. Pete Sanderson, verison 1.0, 23 December 2010.
 */
class BitmapDisplay : AbstractMarsToolAndApplication
{
    // Major GUI components
    private lateinit var uiUnitWidthSelector: JComboBox<Int>
    private lateinit var uiUnitHeightSelector: JComboBox<Int>
    private lateinit var uiWidthSelector: JComboBox<Int>
    private lateinit var uiHeightSelector: JComboBox<Int>
    private lateinit var uiBaseAddressSelector: JComboBox<String>
    private lateinit var canvas: JPanel
    private lateinit var results: JPanel

    // Some GUI settings
    private val emptyBorder = EmptyBorder(4, 4, 4, 4)
    private val backgroundColor = Color.WHITE

    // Values for display canvas.  Note their initialization uses the identifiers just above.
    private val unitWidth get() = uiUnitWidthSelector.getInt()
    private val unitHeight get() = uiUnitHeightSelector.getInt()
    private val displayWidth get() = uiWidthSelector.getInt()
    private val displayHeight get() = uiHeightSelector.getInt()
    private val displayDimension get() = Dimension(displayWidth, displayHeight)

    private val baseAddresses = intArrayOf(Memory.dataSegmentBaseAddress, Memory.globalPointer, Memory.dataBaseAddress,
        Memory.heapBaseAddress, Memory.memoryMapBaseAddress)

    private val baseAddress get() = baseAddresses[uiBaseAddressSelector.selectedIndex]

    private lateinit var grid: Grid

    /**
     * Simple constructor, likely used to run a stand-alone bitmap display tool.
     *
     * @param title String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    constructor(title: String, heading: String) : super(title, heading)

    constructor() : super("Azalea's Modified Bitmap Display", heading)

    override fun getName() = "Bitmap Display"

    /**
     * Override the inherited method, which registers us as an Observer over the static data segment (starting address
     * 0x10010000) only.  This version will register us as observer over the the memory range as selected by the base
     * address combo box and capacity of the visualization display (number of visualization elements times the number of
     * memory words each one represents). It does so by calling the inherited 2-parameter overload of this method. If
     * you use the inherited GUI buttons, this method is invoked when you click "Connect" button on MarsTool or the
     * "Assemble and Run" button on a Mars-based app.
     */
    override fun addAsObserver()
    {
        var highAddress: Int = baseAddress + grid.rows * grid.cols * Memory.WORD_LENGTH_BYTES
        // Special case: baseAddress<0 means we're in kernel memory (0x80000000 and up) and most likely
        // in memory map address space (0xffff0000 and up).  In this case, we need to make sure the high address
        // does not drop off the high end of 32 bit address space.  Highest allowable word address is 0xfffffffc,
        // which is interpreted in Java int as -4.
        if (baseAddress < 0 && highAddress > -4)
        {
            highAddress = -4
        }
        addAsObserver(baseAddress, highAddress)
    }

    /**
     * Method that constructs the main display area.  It is organized vertically into two major components: the display
     * configuration which an be modified using combo boxes, and the visualization display which is updated as the
     * attached MIPS program executes.
     *
     * @return the GUI component containing these two areas
     */
    override fun buildMainDisplayArea(): JComponent
    {
        results = JPanel()
        results.add(buildOrganizationArea())
        results.add(buildVisualizationArea())
        return results
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Rest of the protected methods.  These override do-nothing methods inherited from
    //  the abstract superclass.
    //////////////////////////////////////////////////////////////////////////////////////
    /**
     * Update display when connected MIPS program accesses (data) memory.
     *
     * @param memory the attached memory
     * @param accessNotice information provided by memory in MemoryAccessNotice object
     */
    override fun processMIPSUpdate(memory: Observable, accessNotice: AccessNotice)
    {
        if (accessNotice.accessType == AccessNotice.WRITE)
        {
            updateColorForAddress(accessNotice as MemoryAccessNotice)
        }
    }

    /**
     * Initialize all JComboBox choice structures not already initialized at declaration. Overrides inherited method
     * that does nothing.
     */
    override fun initializePreGUI()
    {
        grid = Grid(0, 0)
    }

    /**
     * The only post-GUI initialization is to create the initial Grid object based on the default settings of the
     * various combo boxes. Overrides inherited method that does nothing.
     */
    override fun initializePostGUI()
    {
        grid = createNewGrid()
    }

    /**
     * Method to reset counters and display when the Reset button selected. Overrides inherited method that does
     * nothing.
     */
    override fun reset()
    {
        resetCounts()
        updateDisplay()
    }

    /**
     * Updates display immediately after each update (AccessNotice) is processed, after display configuration changes as
     * needed, and after each execution step when Mars is running in timed mode.  Overrides inherited method that does
     * nothing.
     */
    override fun updateDisplay() = canvas.repaint()

    /**
     * Overrides default method, to provide a Help button for this tool/app.
     */
    override fun getHelpComponent(): JComponent
    {
        val helpContent = """
             Use this program to simulate a basic bitmap display where
             each memory word in a specified address space corresponds to
             one display pixel in row-major order starting at the upper left
             corner of the display.  This tool may be run either from the
             MARS Tools menu or as a stand-alone application.
             
             You can easily learn to use this small program by playing with
             it!   Each rectangular unit on the display represents one memory
             word in a contiguous address space starting with the specified
             base address.  The value stored in that word will be interpreted
             as a 24-bit RGB color value with the red component in bits 16-23,
             the green component in bits 8-15, and the blue component in bits 0-7.
             Each time a memory word within the display address space is written
             by the MIPS program, its position in the display will be rendered
             in the color that its value represents.
             
             Version 1.0 is very basic and was constructed from the Memory
             Reference Visualization tool's code.  Feel free to improve it and
             send me your code for consideration in the next MARS release.
             
             Contact Pete Sanderson at psanderson@otterbein.edu with
             questions or comments.
             
             """.trimIndent()
        val help = JButton("Help")
        help.addActionListener { JOptionPane.showMessageDialog(theWindow, helpContent) }
        return help
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Private methods defined to support the above.
    //////////////////////////////////////////////////////////////////////////////////////
    // UI components and layout for left half of GUI, where settings are specified.
    private fun buildOrganizationArea(): JComponent
    {
        val organization = JPanel(GridLayout(8, 1))

        uiUnitWidthSelector = JComboBox(arrayOf(1, 2, 4, 8, 16, 32)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 2
            toolTipText = "Width in pixels of rectangle representing memory word"
            addActionListener {
                grid = createNewGrid()
                updateDisplay()
            }
        }

        uiUnitHeightSelector = JComboBox(arrayOf(1, 2, 4, 8, 16, 32)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 2
            toolTipText = "Height in pixels of rectangle representing memory word"
            addActionListener {
                grid = createNewGrid()
                updateDisplay()
            }
        }

        uiWidthSelector = JComboBox(arrayOf(64, 128, 256, 512, 1024)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 3
            toolTipText = "Total width in pixels of display area"
            addActionListener {
                canvas.preferredSize = displayDimension
                canvas.size = displayDimension
                grid = createNewGrid()
                updateDisplay()
            }
        }

        uiHeightSelector = JComboBox(arrayOf(64, 128, 256, 512, 1024)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 2
            toolTipText = "Total height in pixels of display area"
            addActionListener {
                canvas.preferredSize = displayDimension
                canvas.size = displayDimension
                grid = createNewGrid()
                updateDisplay()
            }
        }

        val descriptions = arrayOf("global data", "\$gp", "static data", "heap", "memory map")

        val displayBaseAddressChoices = baseAddresses
            .mapIndexed { i, it -> "${Binary.intToHexString(it)} (${descriptions[i]})" }.toTypedArray()

        uiBaseAddressSelector = JComboBox(displayBaseAddressChoices).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 1
            toolTipText = "Base address for display area (upper left corner)"
            addActionListener {
                // If display base address is changed while connected to MIPS (this can only occur
                // when being used as a MarsTool), we have to delete ourselves as an observer and re-register.
                if (connectButton != null && connectButton.isConnected)
                {
                    deleteAsObserver()
                    addAsObserver()
                }
                grid = createNewGrid()
                updateDisplay()
            }
        }

        // ALL COMPONENTS FOR "ORGANIZATION" SECTION
        val unitWidthInPixelsRow = panelWithBorderLayout
        unitWidthInPixelsRow.border = emptyBorder
        unitWidthInPixelsRow.add(JLabel("Unit Width in Pixels "), BorderLayout.WEST)
        unitWidthInPixelsRow.add(uiUnitWidthSelector, BorderLayout.EAST)
        val unitHeightInPixelsRow = panelWithBorderLayout
        unitHeightInPixelsRow.border = emptyBorder
        unitHeightInPixelsRow.add(JLabel("Unit Height in Pixels "), BorderLayout.WEST)
        unitHeightInPixelsRow.add(uiUnitHeightSelector, BorderLayout.EAST)
        val widthInPixelsRow = panelWithBorderLayout
        widthInPixelsRow.border = emptyBorder
        widthInPixelsRow.add(JLabel("Display Width in Pixels "), BorderLayout.WEST)
        widthInPixelsRow.add(uiWidthSelector, BorderLayout.EAST)
        val heightInPixelsRow = panelWithBorderLayout
        heightInPixelsRow.border = emptyBorder
        heightInPixelsRow.add(JLabel("Display Height in Pixels "), BorderLayout.WEST)
        heightInPixelsRow.add(uiHeightSelector, BorderLayout.EAST)
        val baseAddressRow = panelWithBorderLayout
        baseAddressRow.border = emptyBorder
        baseAddressRow.add(JLabel("Base address for display "), BorderLayout.WEST)
        baseAddressRow.add(uiBaseAddressSelector, BorderLayout.EAST)

        // Lay 'em out in the grid...
        organization.add(unitWidthInPixelsRow)
        organization.add(unitHeightInPixelsRow)
        organization.add(widthInPixelsRow)
        organization.add(heightInPixelsRow)
        organization.add(baseAddressRow)
        return organization
    }

    // UI components and layout for right half of GUI, the visualization display area.
    private fun buildVisualizationArea(): JComponent
    {
        canvas = GraphicsPanel()
        canvas.preferredSize = displayDimension
        canvas.toolTipText = "Bitmap display area"
        return canvas
    }

    // reset all counters in the Grid.
    private fun resetCounts() = grid.reset()

    private fun <T> JComboBox<T>.getInt() = selectedItem!!.toString().toInt()

    // Use this for consistent results.
    private val panelWithBorderLayout: JPanel
        get() = JPanel(BorderLayout(2, 2))

    // Method to determine grid dimensions based on current control settings.
    // Each grid element corresponds to one visualization unit.
    private fun createNewGrid(): Grid
    {
        val rows = displayHeight / unitHeight
        val columns = displayWidth / unitWidth
        return Grid(rows, columns)
    }

    // Given memory address, update color for the corresponding grid element.
    private fun updateColorForAddress(notice: MemoryAccessNotice)
    {
        val address = notice.address
        val value = notice.value
        val offset = (address - baseAddress) / Memory.WORD_LENGTH_BYTES
        try
        {
            grid.setElement(offset / grid.cols, offset % grid.cols, value)
        } catch (e: IndexOutOfBoundsException)
        {
            // If address is out of range for display, do nothing.
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Specialized inner classes for modeling and animation.
    //////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    //  Class that represents the panel for visualizing and animating memory reference
    //  patterns.
    private inner class GraphicsPanel : JPanel()
    {
        // override default paint method to assure display updated correctly every time
        // the panel is repainted.
        override fun paint(g: Graphics) = paintGrid(g, grid)

        // Paint the color codes.
        private fun paintGrid(g: Graphics, grid: Grid)
        {
            var upperLeftX = 0
            var upperLeftY = 0
            for (i in 0 until grid.rows)
            {
                for (j in 0 until grid.cols)
                {
                    g.color = grid.getElementFast(i, j)
                    g.fillRect(upperLeftX, upperLeftY, unitWidth, unitHeight)
                    upperLeftX += unitWidth // faster than multiplying
                }
                // get ready for next row...
                upperLeftX = 0
                upperLeftY += unitHeight // faster than multiplying
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // Represents grid of colors
    private class Grid(rows: Int, columns: Int)
    {
        var grid: Array<Array<Color?>>
        var rows: Int
        var cols: Int

        init
        {
            grid = Array(rows) { arrayOfNulls(columns) }
            this.rows = rows
            this.cols = columns
            reset()
        }

        // Returns value in given grid element; null if row or column is out of range.
        private fun getElement(row: Int, column: Int): Color?
        {
            return if (row in 0..rows && column >= 0 && column <= cols) grid[row][column] else null
        }

        // Returns value in given grid element without doing any row/column index checking.
        // Is faster than getElement but will throw array index out of bounds exception if
        // parameter values are outside the bounds of the grid.
        fun getElementFast(row: Int, column: Int): Color?
        {
            return grid[row][column]
        }

        // Set the grid element.
        fun setElement(row: Int, column: Int, color: Int)
        {
            grid[row][column] = Color(color)
        }

        // Set the grid element.
        private fun setElement(row: Int, column: Int, color: Color)
        {
            grid[row][column] = color
        }

        // Just set all grid elements to black.
        fun reset()
        {
            for (i in 0 until rows)
            {
                for (j in 0 until cols)
                {
                    grid[i][j] = Color.BLACK
                }
            }
        }
    }

    companion object
    {
        private const val version = "Version 1.0"
        private const val heading = "Bitmap Display"

        /**
         * Main provided for pure stand-alone use.  Recommended stand-alone use is to write a driver program that
         * instantiates a Bitmap object then invokes its go() method. "stand-alone" means it is not invoked from the MARS
         * Tools menu.  "Pure" means there is no driver program to invoke the application.
         */
        @JvmStatic
        fun main(args: Array<String>)
        {
            BitmapDisplay("Bitmap Display stand-alone, " + version, heading).go()
        }
    }
}
