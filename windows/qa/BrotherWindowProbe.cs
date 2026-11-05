using System;
using System.Runtime.InteropServices;
using System.Text;

// Somente QA de instalacao Windows; nao integra o runtime comercial.
// Enumera janelas visiveis do desktop para nao depender de MainWindowHandle
// do processo criado pelo wrapper nativo do jpackage.
public static class BrotherWindowProbe
{
    private delegate bool WindowVisitor(IntPtr window, IntPtr context);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(WindowVisitor callback, IntPtr context);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr window);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, StringBuilder text, int capacity);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    public static int FindVisibleWindowOwner(string titlePrefix)
    {
        if (String.IsNullOrWhiteSpace(titlePrefix))
            throw new ArgumentException("Prefixo de título obrigatório.", "titlePrefix");

        int owner = 0;
        WindowVisitor visitor = (window, context) =>
        {
            if (!IsWindowVisible(window))
                return true;
            var title = new StringBuilder(512);
            GetWindowText(window, title, title.Capacity);
            if (!title.ToString().StartsWith(titlePrefix, StringComparison.OrdinalIgnoreCase))
                return true;

            uint processId;
            GetWindowThreadProcessId(window, out processId);
            owner = (int)processId;
            return false;
        };
        EnumWindows(visitor, IntPtr.Zero);
        GC.KeepAlive(visitor);
        return owner;
    }
}
