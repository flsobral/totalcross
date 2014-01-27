#include <wrl/client.h>

#include "esUtil.h"

#if (_MSC_VER >= 1800)
#include <d3d11_2.h>
#else
#include <d3d11_1.h>
#endif

#include "MainView.h"

#include <thread>
#include "winrtangle.h"
#include "../Window.h"

using namespace TotalCross;

using namespace Windows::ApplicationModel::Core;

using namespace Platform;

using namespace Windows::ApplicationModel;
using namespace Windows::ApplicationModel::Core;
using namespace Windows::ApplicationModel::Activation;
using namespace Windows::UI::Core;
using namespace Windows::UI::ViewManagement;
using namespace Windows::Phone::UI::Core;
using namespace Windows::Phone::UI::Input;
using namespace Windows::Foundation;
using namespace Windows::Graphics::Display;

static MainView ^lastInstance = nullptr;

//#include "GLES2/gl2.h"
//#include "GLES2/gl2ext.h"

// Helper class for basic timing.
ref class BasicTimer sealed
{
public:
	// Initializes internal timer values.
	BasicTimer()
	{
		if (!QueryPerformanceFrequency(&m_frequency))
		{
			throw ref new Platform::FailureException();
		}
		Reset();
	}

	// Reset the timer to initial values.
	void Reset()
	{
		Update();
		m_startTime = m_currentTime;
		m_total = 0.0f;
		m_delta = 1.0f / 60.0f;
	}

	// Update the timer's internal values.
	void Update()
	{
		if (!QueryPerformanceCounter(&m_currentTime))
		{
			throw ref new Platform::FailureException();
		}

		m_total = static_cast<float>(
			static_cast<double>(m_currentTime.QuadPart - m_startTime.QuadPart) /
			static_cast<double>(m_frequency.QuadPart)
			);

		if (m_lastTime.QuadPart == m_startTime.QuadPart)
		{
			// If the timer was just reset, report a time delta equivalent to 60Hz frame time.
			m_delta = 1.0f / 60.0f;
		}
		else
		{
			m_delta = static_cast<float>(
				static_cast<double>(m_currentTime.QuadPart - m_lastTime.QuadPart) /
				static_cast<double>(m_frequency.QuadPart)
				);
		}

		m_lastTime = m_currentTime;
	}

	// Duration in seconds between the last call to Reset() and the last call to Update().
	property float Total
	{
		float get() { return m_total; }
	}

	// Duration in seconds between the previous two calls to Update().
	property float Delta
	{
		float get() { return m_delta; }
	}

private:
	LARGE_INTEGER m_frequency;
	LARGE_INTEGER m_currentTime;
	LARGE_INTEGER m_startTime;
	LARGE_INTEGER m_lastTime;
	float m_total;
	float m_delta;
};

MainView::MainView() :
m_windowClosed(false),
m_windowVisible(true)
{
	lastInstance = this;
}

MainView::MainView(String ^cmdline, String ^_vmPath, String ^_appPath) :
m_windowClosed(false),
m_windowVisible(true)
{
	strcpy(cmdLine, "AllTests /cmd ");
	WideCharToMultiByte(CP_ACP, 0, cmdline->Data(), cmdline->Length(), cmdLine + strlen(cmdLine), 512 - strlen(cmdLine), NULL, NULL);
	//WideCharToMultiByte(CP_ACP, 0, _appPath->Data(), _appPath->Length(), appPath, 1024 , NULL, NULL);
	vmPath = _vmPath;
	appPath = _appPath;
	_cmdline = cmdline;

	lastInstance = this;
}

void MainView::Initialize(CoreApplicationView^ applicationView)
{
	applicationView->Activated +=
		ref new TypedEventHandler<CoreApplicationView^, IActivatedEventArgs^>(this, &MainView::OnActivated);

	CoreApplication::Suspending +=
		ref new EventHandler<SuspendingEventArgs^>(this, &MainView::OnSuspending);

	CoreApplication::Resuming +=
		ref new EventHandler<Platform::Object^>(this, &MainView::OnResuming);

   m_renderer = ref new CubeRenderer();
}

void MainView::SetWindow(CoreWindow^ window)
{
   currentWindow = window;

   m_inputBuffer = ref new Windows::Phone::UI::Core::KeyboardInputBuffer();
   m_inputBuffer->InputScope = CoreInputScope::Text;
   m_inputBuffer->TextChanged +=
	   ref new TypedEventHandler<KeyboardInputBuffer^, CoreTextChangedEventArgs^>(this, &MainView::OnTextChange);
   //window->KeyboardInputBuffer = m_inputBuffer; //XXX when we learn how to treat perfectly text change events, we set this on again

	window->VisibilityChanged +=
		ref new TypedEventHandler<CoreWindow^, VisibilityChangedEventArgs^>(this, &MainView::OnVisibilityChanged);

	window->Closed +=
		ref new TypedEventHandler<CoreWindow^, CoreWindowEventArgs^>(this, &MainView::OnWindowClosed);

	window->PointerPressed +=
		ref new TypedEventHandler<CoreWindow^, PointerEventArgs^>(this, &MainView::OnPointerPressed);

	window->PointerMoved +=
		ref new TypedEventHandler<CoreWindow^, PointerEventArgs^>(this, &MainView::OnPointerMoved);

	window->PointerReleased +=
		ref new TypedEventHandler<CoreWindow^, PointerEventArgs^>(this, &MainView::OnPointerReleased);
	window->KeyDown +=
		ref new TypedEventHandler<CoreWindow^, KeyEventArgs^>(this, &MainView::OnKeyDown);
	window->KeyUp +=
		ref new TypedEventHandler<CoreWindow^, KeyEventArgs^>(this, &MainView::OnKeyUp);

	window->PointerWheelChanged +=
		ref new TypedEventHandler<CoreWindow^, PointerEventArgs^>(this, &MainView::OnPointerWheel);

	window->CharacterReceived +=
		ref new TypedEventHandler<CoreWindow^, CharacterReceivedEventArgs^>(this, &MainView::OnCharacterReceived);

	window->PointerCaptureLost +=
		ref new TypedEventHandler<CoreWindow^, PointerEventArgs^>(this, &MainView::OnPointerWheel); 
	window->PointerExited +=
		ref new TypedEventHandler<CoreWindow^, PointerEventArgs^>(this, &MainView::OnPointerWheel);

	window->InputEnabled +=
		ref new TypedEventHandler<CoreWindow^, InputEnabledEventArgs^>(this, &MainView::OnInputEnabled);

	window->SizeChanged +=
		ref new TypedEventHandler<CoreWindow^, WindowSizeChangedEventArgs^>(this, &MainView::OnSizeChanged);

	auto inputPane = InputPane::GetForCurrentView();
	inputPane->Hiding +=
		ref new TypedEventHandler <InputPane ^, InputPaneVisibilityEventArgs^>(this, &MainView::OnHidingSIP);
	inputPane->Showing +=
		ref new TypedEventHandler <InputPane ^, InputPaneVisibilityEventArgs^>(this, &MainView::OnShowingSIP);

   m_renderer->Initialize(CoreWindow::GetForCurrentThread());
}

void MainView::OnSizeChanged(CoreWindow ^sender, WindowSizeChangedEventArgs ^args)
{
	debug("onSizeChanged");
}

void MainView::OnShowingSIP(InputPane ^sender, InputPaneVisibilityEventArgs ^args)
{
	debug("onShowingSIP");
	setShiftYonNextUpdateScreen = true;
}

void MainView::OnHidingSIP(InputPane ^sender, InputPaneVisibilityEventArgs ^args)
{
	debug("onHidingSIP");
	setShiftYonNextUpdateScreen = true;
	eventQueuePush(CONTROLEVENT_SIP_CLOSED, 0, 0, 0, 0);
}

int MainView::GetSIPHeight(void)
{
	return InputPane::GetForCurrentView()->OccludedRect.Height;
}

void MainView::OnInputEnabled(Windows::UI::Core::CoreWindow^ sender, Windows::UI::Core::InputEnabledEventArgs^ args)
{
	debug("OnInputEnabled");
}

void MainView::OnCharacterReceived(Windows::UI::Core::CoreWindow^ sender, Windows::UI::Core::CharacterReceivedEventArgs^ args)
{
	auto k = args->KeyCode;

	// When enter is pressed, only a single character \r is sent; as TC recognizes '\n', we change its value beforehand
	if (k == '\r')
		k = '\n';
	debug("caracter recebido: %c", k);
	debug("caracter recebido: %hhd", k);

	eventQueuePush(KEYEVENT_KEY_PRESS, k, k, 0, -1);
}

void MainView::OnPointerWheel(Windows::UI::Core::CoreWindow^ sender, Windows::UI::Core::PointerEventArgs^ args)
{
	debug("N�O APENAS pointer wheel, serah que era isso?");
}

void MainView::Load(Platform::String^ entryPoint)
{

}

void MainView::Run()
{
#if 1
	BasicTimer^ timer = ref new BasicTimer();
	int32 vm_err_code = startVM(cmdLine, &local_context);
	if (vm_err_code != 0)
	{
		m_windowClosed = true;
	}

	set_dispatcher();

   if (!m_windowClosed)
      startProgram(local_context);
#else
   BasicTimer^ timer = ref new BasicTimer();
   m_renderer->setup();

   while (!m_windowClosed)
   {
      if (m_windowVisible)
      {
         timer->Update();
         CoreWindow::GetForCurrentThread()->Dispatcher->ProcessEvents(CoreProcessEventsOption::ProcessAllIfPresent);
         m_renderer->Render();
         m_renderer->Present(); // This call is synchronized to the display frame rate.
      }
      else
      {
         CoreWindow::GetForCurrentThread()->Dispatcher->ProcessEvents(CoreProcessEventsOption::ProcessOneAndAllPending);
      }
   }
#endif
}

bool MainView::dxSetup()
{
   m_renderer->setup();
   return true;
}

void MainView::dxUpdateScreen()
{
   CoreWindow::GetForCurrentThread()->Dispatcher->ProcessEvents(CoreProcessEventsOption::ProcessAllIfPresent);
   m_renderer->Render();
   m_renderer->Present(); // This call is synchronized to the display frame rate.
}

void MainView::dxDrawLine(int x1, int y1, int x2, int y2, int color)
{
   m_renderer->drawLine(x1, y1, x2, y2, color);
}

void MainView::dxFillRect(int x1, int y1, int x2, int y2, int color)
{
   m_renderer->fillRect(x1, y1, x2, y2, color);
}

void MainView::dxDrawPixels(int *x, int *y, int count, int color)
{
   m_renderer->drawPixels(x, y, count, color);
}

void MainView::Uninitialize()
{
   m_windowClosed = true;
}

void MainView::OnKeyDown(CoreWindow ^sender, KeyEventArgs ^args)
{
}

void MainView::OnKeyUp(CoreWindow ^sender, KeyEventArgs ^args)
{
	auto k = args->VirtualKey;

	debug("keyup");
	if (k == Windows::System::VirtualKey::Back)
		eventQueuePush(KEYEVENT_SPECIALKEY_PRESS, SK_BACKSPACE, SK_BACKSPACE, 0, -1);
}

void MainView::OnTextChange(KeyboardInputBuffer ^sender, CoreTextChangedEventArgs ^args)
{
	debug("text change");

	auto x = m_inputBuffer->Text->Data();
	auto len = m_inputBuffer->Text->Length();
	unsigned int i;
	char s[1024];

	for (i = 0; i < len; i++) {
		s[i] = (char)x[i];
	}
	s[i] = '\0';

	debug("input ateh agora: %s", s);
//	eventQueuePush(PENEVENT_PEN_DOWN, 0, lastX = pos.X, lastY = pos.Y, -1);
}

void MainView::OnVisibilityChanged(CoreWindow^ sender, VisibilityChangedEventArgs^ args)
{
	m_windowVisible = args->Visible;
	debug("onVisibilityChange");
}

void MainView::OnWindowClosed(CoreWindow^ sender, CoreWindowEventArgs^ args)
{
	m_windowClosed = true;
	debug("onWindowClose");
}

void MainView::OnPointerPressed(CoreWindow^ sender, PointerEventArgs^ args)
{
	auto pos = args->CurrentPoint->Position;
	debug("pressed lastY %.2f lastX %.2f Y %.2f X %.2f", lastY, lastX, pos.Y, pos.X);
	eventQueuePush(PENEVENT_PEN_DOWN, 0, lastX = pos.X, lastY = pos.Y - glShiftY, -1);
}

void MainView::OnPointerMoved(CoreWindow^ sender, PointerEventArgs^ args)
{
	// Insert your code here.
	auto pos = args->CurrentPoint->Position;
	if (lastX != pos.X || lastY != pos.Y) {
		debug("moving lastY %.2f lastX %.2f Y %.2f X %.2f", lastY, lastX, pos.Y, pos.X);
		eventQueuePush(PENEVENT_PEN_DRAG, 0, lastX = pos.X, lastY = pos.Y - glShiftY, -1);
		isDragging = true;
	}
}

void MainView::OnPointerReleased(CoreWindow^ sender, PointerEventArgs^ args)
{
	auto pos = args->CurrentPoint->Position;
	eventQueuePush(PENEVENT_PEN_UP, 0, lastX = pos.X, lastY = pos.Y - glShiftY, -1);
	isDragging = false;
}

void MainView::OnActivated(CoreApplicationView^ applicationView, IActivatedEventArgs^ args)
{
	CoreWindow::GetForCurrentThread()->Activate();

	HardwareButtons::BackPressed +=
		ref new EventHandler<BackPressedEventArgs^>(this, &MainView::OnBackPressed);
	debug("onActivate");
}

void MainView::OnBackPressed(Object ^sender, BackPressedEventArgs ^args)
{
	debug("onBackPressed");
	if (currentWindow.Get()->IsKeyboardInputEnabled) {
		eventQueuePush(CONTROLEVENT_SIP_CLOSED, 0, 0, 0, 0);
	}
	args->Handled = true;
}

void MainView::OnSuspending(Platform::Object^ sender, SuspendingEventArgs^ args)
{
   // Save app state asynchronously after requesting a deferral. Holding a deferral
   // indicates that the application is busy performing suspending operations. Be
   // aware that a deferral may not be held indefinitely. After about five seconds,
   // the app will be forced to exit.
	debug("onSuspending");
   SuspendingDeferral^ deferral = args->SuspendingOperation->GetDeferral();
   
   //if (currentDirect3DBase)
    //  currentDirect3DBase->ReleaseResourcesForSuspending();

   //create_task([this, deferral]()
   //{
      // Insert your code here.

      deferral->Complete();
   //});
}

void MainView::OnResuming(Platform::Object^ sender, Platform::Object^ args)
{
	debug("onResuming");
	// Restore any data or state that was unloaded on suspend. By default, data
	// and state are persisted when resuming from suspend. Note that this event
	// does not occur if the app was previously terminated.
   
   m_renderer->CreateWindowSizeDependentResources();
}

MainView ^MainView::GetLastInstance()
{
	return lastInstance;
}

String ^MainView::getAppPath()
{
	return appPath;
}

String ^MainView::getVmPath()
{
	return vmPath;
}

Windows::UI::Core::CoreWindow^ MainView::GetWindow()
{
   return currentWindow.Get();
}

void MainView::setKeyboard(int kb)
{
	currentWindow.Get()->KeyboardInputBuffer = nullptr;// = m_inputBuffer;

	switch ((enum TCSIP) kb)
	{
	case SIP_ENABLE_NUMERICPAD:
		//m_inputBuffer->InputScope = CoreInputScope::Number;
		break;
	case SIP_DISABLE_NUMERICPAD:
		//m_inputBuffer->InputScope = CoreInputScope::Text;
		break;
	case SIP_HIDE:
		currentWindow.Get()->IsKeyboardInputEnabled = false;
		break;
	case SIP_TOP:
	case SIP_BOTTOM:
	case SIP_SHOW:
		currentWindow.Get()->IsKeyboardInputEnabled = true;
		break;
	}
}