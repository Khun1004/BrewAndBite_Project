package com.example.brewandbite.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.brewandbite.R
import com.example.brewandbite.common.setup
import com.example.brewandbite.databinding.FragmentOrderBinding

/**
 * Order 탭 (헤더 아래는 빈 화면 · 팀원 작업 공간)
 * 헤더는 공통 헤더(layout_page_header.xml)라서 따로 만들 필요 없음
 *
 * 이미 연결되어 있는 곳:
 *  - 하단 탭 바의 Order 탭 → 이 화면
 *  - Cart 화면의 '주문하기' 버튼 → 이 화면
 *
 * 쓸 수 있는 장바구니 데이터 (cart/CartManager.kt):
 *  - CartManager.getItems()    담긴 메뉴 목록
 *  - CartManager.totalPrice()  총 금액  (금액 표시: totalPrice().toWon())
 *  - CartManager.clear()       주문 완료 후 장바구니 비우기
 *
 * 로그인 / 회원 정보 (auth 폴더):
 *  - Cart의 '주문하기'는 로그인한 사람만 이 화면으로 온다 (로그인 안 했으면 로그인 화면 먼저)
 *  - AuthManager.currentUid()          내 회원 ID (주문 저장 위치: orders/{회원ID})
 *  - UserRepository.loadMyProfile { }  이름·연락처·주소 불러와서 배달 정보 칸 자동 입력
 *
 * 다른 탭으로 이동: (requireActivity() as MainActivity).showTab(MainActivity.TAB_HOME)
 */
class OrderFragment : Fragment() {

    private var _binding: FragmentOrderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 공통 헤더 (Cart / Store / My Page와 같은 모양)
        binding.pageHeader.setup(
            R.string.header_order_eyebrow, R.string.header_order_title, "🧾",
            getString(R.string.header_order_subtitle)
        )

        // TODO (팀원): 여기부터 Order 화면 코드를 작성하세요
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}